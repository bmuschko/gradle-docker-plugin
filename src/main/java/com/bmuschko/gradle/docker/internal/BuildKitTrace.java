/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.internal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The build progress BuildKit reports in a {@code moby.buildkit.trace} message.
 * <p>
 * BuildKit encodes its {@code StatusResponse} as Protocol Buffers, base64-encoded into the {@code aux} field of a
 * build response item. Reading it needs only the handful of fields below, so they are decoded straight off the wire
 * rather than by pulling in a Protocol Buffers runtime and BuildKit's generated types.
 *
 * @see <a href="https://github.com/moby/buildkit/blob/master/api/services/control/control.proto">control.proto</a>
 */
final class BuildKitTrace {

    private static final int WIRE_VARINT = 0;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_BYTES = 2;
    private static final int WIRE_FIXED32 = 5;

    private static final int STATUS_VERTEX = 1;
    private static final int STATUS_LOG = 3;

    private static final int VERTEX_DIGEST = 1;
    private static final int VERTEX_NAME = 3;
    private static final int VERTEX_CACHED = 4;
    private static final int VERTEX_ERROR = 7;

    private static final int LOG_VERTEX = 1;
    private static final int LOG_MESSAGE = 4;

    private final List<Step> steps;

    private final List<Output> outputs;

    private BuildKitTrace(List<Step> steps, List<Output> outputs) {
        this.steps = steps;
        this.outputs = outputs;
    }

    /**
     * Decodes one trace payload. Never throws: a payload this method cannot make sense of yields an empty trace,
     * because failing to render progress must never fail a build that is otherwise fine.
     */
    static BuildKitTrace parse(byte[] payload) {
        List<Step> steps = new ArrayList<>();
        List<Output> outputs = new ArrayList<>();

        try {
            for (Field field : new Reader(payload, 0, payload.length)) {
                if (field.number == STATUS_VERTEX && field.isBytes()) {
                    Step step = parseStep(field.bytes());

                    if (step != null) {
                        steps.add(step);
                    }
                } else if (field.number == STATUS_LOG && field.isBytes()) {
                    Output output = parseOutput(field.bytes());

                    if (output != null) {
                        outputs.add(output);
                    }
                }
            }
        } catch (RuntimeException e) {
            // A payload we cannot read is reported as no progress rather than as a build failure.
            return new BuildKitTrace(Collections.unmodifiableList(steps), Collections.unmodifiableList(outputs));
        }

        return new BuildKitTrace(Collections.unmodifiableList(steps), Collections.unmodifiableList(outputs));
    }

    private static Step parseStep(Reader reader) {
        String id = null;
        String name = null;
        boolean cached = false;
        String error = null;

        for (Field field : reader) {
            switch (field.number) {
                case VERTEX_DIGEST:
                    if (field.isBytes()) {
                        id = field.text();
                    }
                    break;
                case VERTEX_NAME:
                    if (field.isBytes()) {
                        name = field.text();
                    }
                    break;
                case VERTEX_CACHED:
                    cached = field.isVarint() && field.value != 0;
                    break;
                case VERTEX_ERROR:
                    if (field.isBytes()) {
                        error = field.text();
                    }
                    break;
                default:
                    break;
            }
        }

        return name == null ? null : new Step(id, name, cached, error);
    }

    private static Output parseOutput(Reader reader) {
        String stepId = null;
        String text = null;

        for (Field field : reader) {
            if (field.number == LOG_VERTEX && field.isBytes()) {
                stepId = field.text();
            } else if (field.number == LOG_MESSAGE && field.isBytes()) {
                text = field.text();
            }
        }

        return text == null ? null : new Output(stepId, text);
    }

    /**
     * Walks the Protocol Buffers wire format: a sequence of fields, each a varint key holding a field number and a
     * wire type, followed by the field's value.
     *
     * @see <a href="https://protobuf.dev/programming-guides/encoding/">Protocol Buffers encoding</a>
     */
    private static final class Reader implements Iterable<Field>, Iterator<Field> {

        private final byte[] buffer;
        private final int limit;
        private int position;

        private Reader(byte[] buffer, int position, int limit) {
            this.buffer = buffer;
            this.position = position;
            this.limit = limit;
        }

        @Override
        public Iterator<Field> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return position < limit;
        }

        @Override
        public Field next() {
            long key = varint();
            int number = (int) (key >>> 3);
            int wireType = (int) (key & 0x7);

            switch (wireType) {
                case WIRE_VARINT:
                    return new Field(number, wireType, varint(), buffer, 0, 0);
                case WIRE_BYTES: {
                    int length = (int) varint();
                    int from = position;
                    skip(length);
                    return new Field(number, wireType, 0, buffer, from, length);
                }
                case WIRE_FIXED64:
                    skip(8);
                    return new Field(number, wireType, 0, buffer, 0, 0);
                case WIRE_FIXED32:
                    skip(4);
                    return new Field(number, wireType, 0, buffer, 0, 0);
                default:
                    throw new IllegalArgumentException("Unsupported wire type: " + wireType);
            }
        }

        private void skip(int length) {
            if (length < 0 || position + length > limit) {
                throw new IllegalArgumentException("Field runs past the end of the payload");
            }

            position += length;
        }

        private long varint() {
            long result = 0;

            for (int shift = 0; shift < 64; shift += 7) {
                if (position >= limit) {
                    throw new IllegalArgumentException("Payload ended mid-value");
                }

                byte read = buffer[position++];
                result |= ((long) (read & 0x7f)) << shift;

                if ((read & 0x80) == 0) {
                    return result;
                }
            }

            throw new IllegalArgumentException("Value is not a valid varint");
        }
    }

    private static final class Field {

        private final int number;
        private final int wireType;
        private final long value;
        private final byte[] buffer;
        private final int from;
        private final int length;

        private Field(int number, int wireType, long value, byte[] buffer, int from, int length) {
            this.number = number;
            this.wireType = wireType;
            this.value = value;
            this.buffer = buffer;
            this.from = from;
            this.length = length;
        }

        private boolean isBytes() {
            return wireType == WIRE_BYTES;
        }

        private boolean isVarint() {
            return wireType == WIRE_VARINT;
        }

        private Reader bytes() {
            return new Reader(buffer, from, from + length);
        }

        private String text() {
            return new String(buffer, from, length, StandardCharsets.UTF_8);
        }
    }

    List<Step> getSteps() {
        return steps;
    }

    List<Output> getOutputs() {
        return outputs;
    }

    /**
     * One node of the build graph, such as a single Dockerfile instruction.
     */
    static final class Step {

        private final String id;
        private final String name;
        private final boolean cached;
        private final String error;

        Step(String id, String name, boolean cached, String error) {
            this.id = id;
            this.name = name;
            this.cached = cached;
            this.error = error;
        }

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }

        boolean isCached() {
            return cached;
        }

        String getError() {
            return error;
        }
    }

    /**
     * Something a step printed, on either standard output or standard error.
     */
    static final class Output {

        private final String stepId;
        private final String text;

        Output(String stepId, String text) {
            this.stepId = stepId;
            this.text = text;
        }

        String getStepId() {
            return stepId;
        }

        String getText() {
            return text;
        }
    }
}
