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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.transport.DockerHttpClient;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Routes image builds to BuildKit by asking the Docker Engine API for builder version 2, and presents the BuildKit
 * response stream in the shape the Docker Java library expects.
 * <p>
 * The Engine API's classic builder does not understand BuildKit's Dockerfile syntax, so a {@code RUN --mount}
 * instruction fails with an {@code the --mount option requires BuildKit} error message. Builds performed by the classic
 * builder are also absent from BuildKit's build history, which is what is listed by {@code docker buildx history}
 * command.
 * <p>
 * A BuildKit build reports progress as {@code moby.buildkit.trace} messages carrying a base64-encoded protobuf
 * payload, and reports the image it produced as a {@code moby.image.id} message. The Docker Java library models the
 * {@code aux} field of a response item as a fixed object and cannot read the former at all, so trace messages are
 * dropped and the image id message is presented the way the classic builder reports it. Failures already arrive in
 * the classic shape and are passed through untouched.
 * <p>
 * This class exists only because the Docker Java library has no BuildKit support of its own. It should be removed
 * once it does.
 *
 * @see <a href="https://github.com/docker-java/docker-java/issues/2361">docker-java#2361</a>
 * @since 10.1.0
 */
public class BuildKitDockerHttpClient implements DockerHttpClient {

    private static final String IMAGE_BUILD_RESOURCE = "/build";

    private static final String BUILDER_VERSION_BUILDKIT = "2";

    private static final String TRACE_MESSAGE = "moby.buildkit.trace";

    private static final String IMAGE_ID_MESSAGE = "moby.image.id";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DockerHttpClient delegate;

    public BuildKitDockerHttpClient(DockerHttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Response execute(Request request) {
        if (!isImageBuild(request)) {
            return delegate.execute(request);
        }

        return new BuildKitResponse(delegate.execute(withBuildKitParameters(request)));
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private static boolean isImageBuild(Request request) {
        if (!"POST".equalsIgnoreCase(request.method())) {
            return false;
        }

        return resourceOf(request.path()).endsWith(IMAGE_BUILD_RESOURCE);
    }

    private static String resourceOf(String path) {
        int query = path.indexOf('?');
        return query == -1 ? path : path.substring(0, query);
    }

    private static Request withBuildKitParameters(Request request) {
        String path = request.path();
        String separator = path.indexOf('?') == -1 ? "?" : "&";

        return Request.builder()
                .from(request)
                .path(path + separator + "version=" + BUILDER_VERSION_BUILDKIT + "&buildid=" + UUID.randomUUID())
                .build();
    }

    /**
     * Reports an image id the way the classic builder does, so that a {@code BuildImageResultCallback} recognises
     * the build as successful and can resolve the image id from it.
     */
    private static String asStreamMessage(String text) {
        ObjectNode streamMessage = MAPPER.createObjectNode();
        streamMessage.put("stream", text + "\n");
        return streamMessage.toString();
    }

    /**
     * Turns BuildKit's build progress into the line-per-message stream the Docker Java library expects, keeping the
     * numbering of steps stable for the length of one build so that output from steps running at the same time can
     * be told apart.
     */
    private static final class ProgressTranslator {

        private final Map<String, Integer> stepNumbers = new HashMap<>();

        private final Set<String> named = new HashSet<>();

        private final Set<String> finished = new HashSet<>();

        /**
         * Translates a single BuildKit response message into the messages to pass on, which may be none (progress
         * that adds nothing) or several (a step that both started and printed something).
         */
        private List<String> translate(String message) {
            if (message.trim().isEmpty()) {
                return Collections.emptyList();
            }

            JsonNode parsed;

            try {
                parsed = MAPPER.readTree(message);
            } catch (IOException e) {
                return Collections.singletonList(message);
            }

            String id = parsed.path("id").asText("");

            if (TRACE_MESSAGE.equals(id)) {
                return progressOf(parsed.path("aux").asText(""));
            }

            if (IMAGE_ID_MESSAGE.equals(id)) {
                String imageId = parsed.path("aux").path("ID").asText("");
                return Collections.singletonList(imageId.isEmpty() ? message : asStreamMessage(imageId));
            }

            return Collections.singletonList(message);
        }

        private List<String> progressOf(String encodedTrace) {
            if (encodedTrace.isEmpty()) {
                return Collections.emptyList();
            }

            byte[] payload;

            try {
                payload = Base64.getDecoder().decode(encodedTrace);
            } catch (IllegalArgumentException e) {
                return Collections.emptyList();
            }

            BuildKitTrace trace = BuildKitTrace.parse(payload);
            List<String> messages = new ArrayList<>();

            for (BuildKitTrace.Step step : trace.getSteps()) {
                String prefix = "#" + numberOf(step.getId());

                if (named.add(step.getId())) {
                    messages.add(asStreamMessage(prefix + " " + step.getName()));
                }

                if (step.getError() != null && finished.add(step.getId())) {
                    messages.add(asStreamMessage(prefix + " ERROR: " + step.getError()));
                } else if (step.isCached() && finished.add(step.getId())) {
                    messages.add(asStreamMessage(prefix + " CACHED"));
                }
            }

            for (BuildKitTrace.Output output : trace.getOutputs()) {
                String prefix = "#" + numberOf(output.getStepId());

                for (String line : output.getText().split("\n", -1)) {
                    if (!line.trim().isEmpty()) {
                        messages.add(asStreamMessage(prefix + " " + line));
                    }
                }
            }

            return messages;
        }

        private int numberOf(@Nullable String stepId) {
            String key = stepId == null ? "" : stepId;
            Integer existing = stepNumbers.get(key);

            if (existing != null) {
                return existing;
            }

            int assigned = stepNumbers.size() + 1;
            stepNumbers.put(key, assigned);
            return assigned;
        }
    }

    private static final class BuildKitResponse implements Response {

        private final Response delegate;

        private InputStream body;

        private BuildKitResponse(Response delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getStatusCode() {
            return delegate.getStatusCode();
        }

        @Override
        public Map<String, List<String>> getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public String getHeader(String name) {
            return delegate.getHeader(name);
        }

        @Override
        public InputStream getBody() {
            if (body == null) {
                body = new TranslatingInputStream(delegate.getBody(), new ProgressTranslator());
            }

            return body;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    /**
     * Reads newline-delimited JSON messages from the daemon and emits the translated ones, a message at a time, so
     * that build progress still reaches the caller while the build is running.
     */
    private static final class TranslatingInputStream extends InputStream {

        private final BufferedReader reader;

        private final ProgressTranslator translator;

        private final Deque<String> pending = new ArrayDeque<>();

        private byte[] buffer = new byte[0];

        private int position;

        private boolean exhausted;

        private TranslatingInputStream(InputStream source, ProgressTranslator translator) {
            this.reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
            this.translator = translator;
        }

        @Override
        public int read() throws IOException {
            if (!buffered()) {
                return -1;
            }

            return buffer[position++] & 0xFF;
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }

            if (!buffered()) {
                return -1;
            }

            int count = Math.min(length, buffer.length - position);
            System.arraycopy(buffer, position, destination, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        private boolean buffered() throws IOException {
            while (position == buffer.length) {
                if (!pending.isEmpty()) {
                    buffer = (pending.removeFirst() + "\n").getBytes(StandardCharsets.UTF_8);
                    position = 0;
                    continue;
                }

                if (exhausted) {
                    return false;
                }

                String message = reader.readLine();

                if (message == null) {
                    exhausted = true;
                    return false;
                }

                pending.addAll(translator.translate(message));
            }

            return true;
        }
    }
}
