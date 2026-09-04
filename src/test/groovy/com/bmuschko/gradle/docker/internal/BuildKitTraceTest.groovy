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
package com.bmuschko.gradle.docker.internal

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class BuildKitTraceTest extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    def "reads the name of every build step BuildKit reports"() {
        when:
        List<BuildKitTrace.Step> steps = stepsOf('build-with-output.ndjson')

        then:
        steps.collect { it.name }.any { it.startsWith('[2/2] RUN echo HELLO-STDOUT') }
        steps.collect { it.name }.any { it.startsWith('[1/2] FROM docker.io/library/alpine') }
    }

    def "reads what a step printed on standard output and standard error"() {
        when:
        List<String> printed = outputsOf('build-with-output.ndjson').collect { it.text }

        then:
        printed.any { it.contains('HELLO-STDOUT') }
        printed.any { it.contains('HELLO-STDERR') }
    }

    def "attributes printed output to the step that produced it"() {
        given:
        List<BuildKitTrace.Output> outputs = outputsOf('build-with-output.ndjson')

        expect:
        outputs.every { it.stepId?.startsWith('sha256:') }
    }

    def "reads which steps were served from cache"() {
        when:
        List<BuildKitTrace.Step> steps = stepsOf('cached-build.ndjson')

        then:
        steps.any { it.cached }
        steps.findAll { it.cached }.collect { it.name }.any { it.contains('RUN echo HELLO-STDOUT') }
    }

    def "reads the error reported against a failed step"() {
        when:
        List<BuildKitTrace.Step> steps = stepsOf('failed-step.ndjson')

        then:
        BuildKitTrace.Step failed = steps.find { it.error }
        failed
        failed.name.contains('RUN echo BEFORE-FAIL')
        failed.error.contains('did not complete successfully')
    }

    def "survives a payload it cannot make sense of"() {
        expect:
        BuildKitTrace.parse(payload).steps.isEmpty()
        BuildKitTrace.parse(payload).outputs.isEmpty()

        where:
        payload << [
                new byte[0],
                [0x08] as byte[],                            // varint key, no value
                [0x0a, 0x7f] as byte[],                      // length 127, no bytes
                [0xff, 0xff, 0xff, 0xff, 0xff] as byte[],    // runaway varint
                'not protobuf at all'.getBytes(StandardCharsets.UTF_8)
        ]
    }

    private static List<BuildKitTrace.Step> stepsOf(String fixture) {
        traces(fixture).collectMany { it.steps }
    }

    private static List<BuildKitTrace.Output> outputsOf(String fixture) {
        traces(fixture).collectMany { it.outputs }
    }

    private static List<BuildKitTrace> traces(String fixture) {
        BuildKitTraceTest.getResourceAsStream("/buildkit/$fixture")
                .getText(StandardCharsets.UTF_8.name())
                .readLines()
                .findAll { !it.trim().isEmpty() }
                .collect { MAPPER.readTree(it) }
                .findAll { it.path('id').asText() == 'moby.buildkit.trace' }
                .collect { BuildKitTrace.parse(Base64.decoder.decode(it.path('aux').asText())) }
    }
}
