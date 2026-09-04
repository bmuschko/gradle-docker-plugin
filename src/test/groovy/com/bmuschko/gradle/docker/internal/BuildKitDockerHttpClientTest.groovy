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
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.transport.DockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient.Request
import com.github.dockerjava.transport.DockerHttpClient.Response
import spock.lang.Specification
import spock.lang.Subject

import java.nio.charset.StandardCharsets

class BuildKitDockerHttpClientTest extends Specification {

    static final ObjectMapper DOCKER_JAVA_MAPPER =
            DefaultDockerClientConfig.createDefaultConfigBuilder().build().getObjectMapper()

    RecordingDockerHttpClient delegate = new RecordingDockerHttpClient()

    @Subject
    BuildKitDockerHttpClient client = new BuildKitDockerHttpClient(delegate)

    def "routes an image build to BuildKit"() {
        when:
        client.execute(request('POST', '/v1.44/build'))

        then:
        queryParameters(delegate.lastRequest)['version'] == ['2']
    }

    def "gives an image build an identifier so it can be cancelled"() {
        when:
        client.execute(request('POST', '/v1.44/build'))

        then:
        queryParameters(delegate.lastRequest)['buildid'].size() == 1
        queryParameters(delegate.lastRequest)['buildid'].first()
    }

    def "keeps the query parameters the build already had"() {
        when:
        client.execute(request('POST', '/v1.44/build?t=example%3Alatest&nocache=true'))

        then:
        with(queryParameters(delegate.lastRequest)) {
            it['t'] == ['example%3Alatest']
            it['nocache'] == ['true']
            it['version'] == ['2']
        }
    }

    def "routes an image build to BuildKit when the daemon path carries no API version"() {
        when:
        client.execute(request('POST', '/build'))

        then:
        queryParameters(delegate.lastRequest)['version'] == ['2']
    }

    def "leaves requests other than image builds alone"() {
        when:
        client.execute(request(method, path))

        then:
        delegate.lastRequest.path() == path

        where:
        method   | path
        'GET'    | '/v1.44/_ping'
        'GET'    | '/v1.44/images/json'
        'POST'   | '/v1.44/images/create?fromImage=busybox'
        'GET'    | '/v1.44/build'
        'POST'   | '/v1.44/build/cancel?id=abc'
        'POST'   | '/v1.44/build/prune'
        'POST'   | '/v1.44/containers/rebuild'
    }

    def "hides the BuildKit trace messages docker-java cannot read"() {
        given:
        delegate.responseBody = fixture('successful-build.ndjson')

        when:
        List<String> messages = executeBuild()

        then:
        !messages.isEmpty()
        messages.every { !it.contains('moby.buildkit.trace') }
    }

    def "every message it emits can be read by docker-java"() {
        given:
        delegate.responseBody = fixture(fixtureName)

        when:
        List<String> messages = executeBuild()

        then:
        messages.each { DOCKER_JAVA_MAPPER.readValue(it, BuildResponseItem) }

        where:
        fixtureName << ['successful-build.ndjson', 'failed-build.ndjson',
                        'build-with-output.ndjson', 'cached-build.ndjson', 'failed-step.ndjson']
    }

    def "reports the built image so the image id can be awaited"() {
        given:
        delegate.responseBody = fixture('successful-build.ndjson')

        when:
        List<BuildResponseItem> items = executeBuild()
                .collect { DOCKER_JAVA_MAPPER.readValue(it, BuildResponseItem) }

        then:
        BuildResponseItem built = items.find { it.isBuildSuccessIndicated() }
        built
        built.imageId == '07e274f8733cf449265ad1d91cf8ce20cc28669fc4d923de364e5c4748138e4b'
    }

    def "lets a failed build stay failed"() {
        given:
        delegate.responseBody = fixture('failed-build.ndjson')

        when:
        List<BuildResponseItem> items = executeBuild()
                .collect { DOCKER_JAVA_MAPPER.readValue(it, BuildResponseItem) }

        then:
        BuildResponseItem failure = items.find { it.isErrorIndicated() }
        failure
        failure.error.contains('did not complete successfully: exit code: 3')
        !items.any { it.isBuildSuccessIndicated() }
    }

    def "leaves the response of other requests untouched"() {
        given:
        delegate.responseBody = '{"id":"moby.buildkit.trace","aux":"AAAA"}\n'

        when:
        String body = client.execute(request('GET', '/v1.44/_ping')).body.text

        then:
        body == '{"id":"moby.buildkit.trace","aux":"AAAA"}\n'
    }

    def "copes with blank lines in the response"() {
        given:
        delegate.responseBody = '\n{"stream":"one"}\n\n{"stream":"two"}\n'

        when:
        List<String> messages = executeBuild()

        then:
        messages == ['{"stream":"one"}', '{"stream":"two"}']
    }

    def "copes with a response that does not end in a newline"() {
        given:
        delegate.responseBody = '{"stream":"only"}'

        when:
        List<String> messages = executeBuild()

        then:
        messages == ['{"stream":"only"}']
    }

    def "shows each build step as BuildKit runs it"() {
        given:
        delegate.responseBody = fixture('build-with-output.ndjson')

        when:
        List<String> log = buildLog()

        then:
        log.any { it.contains('[1/2] FROM docker.io/library/alpine') }
        log.any { it.contains('[2/2] RUN echo HELLO-STDOUT') }
    }

    def "shows what a RUN instruction printed"() {
        given:
        delegate.responseBody = fixture('build-with-output.ndjson')

        when:
        List<String> log = buildLog()

        then:
        log.any { it.contains('HELLO-STDOUT') }
        log.any { it.contains('HELLO-STDERR') }
    }

    def "marks a step that was served from cache"() {
        given:
        delegate.responseBody = fixture('cached-build.ndjson')

        when:
        List<String> log = buildLog()

        then:
        log.any { it.contains('RUN echo HELLO-STDOUT') }
        log.any { it.contains('CACHED') }
    }

    def "shows the error reported against a failed step"() {
        given:
        delegate.responseBody = fixture('failed-step.ndjson')

        when:
        List<String> log = buildLog()

        then:
        log.any { it.contains('RUN echo BEFORE-FAIL') }
        log.any { it.contains('did not complete successfully') }
    }

    def "names each step only once however often BuildKit reports it"() {
        given:
        delegate.responseBody = fixture('build-with-output.ndjson')

        when:
        List<String> log = buildLog()

        then:
        log.count { it.contains('[2/2] RUN echo HELLO-STDOUT') } == 1
    }

    def "numbers the steps so concurrent output can be told apart"() {
        given:
        delegate.responseBody = fixture('build-with-output.ndjson')

        when:
        List<String> log = buildLog()
        List<String> steps = log.findAll { !it.startsWith('sha256:') }

        then:
        steps.every { it =~ /^#\d+ / }
        steps.collect { (it =~ /^#(\d+) /)[0][1] }.unique().size() > 1
    }

    def "attributes a step's output to the same number as the step itself"() {
        given:
        delegate.responseBody = fixture('build-with-output.ndjson')

        when:
        List<String> log = buildLog()
        String runStep = log.find { it.contains('[2/2] RUN echo HELLO-STDOUT') }
        String printed = log.find { it.contains('HELLO-STDOUT') && !it.contains('[2/2]') }

        then:
        runStep && printed
        (runStep =~ /^#(\d+) /)[0][1] == (printed =~ /^#(\d+) /)[0][1]
    }

    private List<String> buildLog() {
        executeBuild()
                .collect { DOCKER_JAVA_MAPPER.readTree(it) }
                .collect { it.path('stream').asText('') }
                .findAll { it }
                .collectMany { it.readLines() }
                .findAll { !it.trim().isEmpty() }
    }

    private List<String> executeBuild() {
        client.execute(request('POST', '/v1.44/build')).body.getText(StandardCharsets.UTF_8.name())
                .readLines()
                .findAll { !it.trim().isEmpty() }
    }

    private static String fixture(String name) {
        BuildKitDockerHttpClientTest.getResourceAsStream("/buildkit/$name").getText(StandardCharsets.UTF_8.name())
    }

    private static Request request(String method, String path) {
        Request.builder().method(method).path(path).build()
    }

    private static Map<String, List<String>> queryParameters(Request request) {
        int separator = request.path().indexOf('?')

        if (separator == -1) {
            return [:]
        }

        request.path().substring(separator + 1)
                .split('&')
                .collect { it.split('=', 2) }
                .groupBy { it[0] }
                .collectEntries { name, pairs -> [name, pairs.collect { it.size() > 1 ? it[1] : '' }] }
    }

    static class RecordingDockerHttpClient implements DockerHttpClient {

        Request lastRequest
        String responseBody = ''

        @Override
        Response execute(Request request) {
            this.lastRequest = request
            new StubResponse(responseBody)
        }

        @Override
        void close() throws IOException {
        }
    }

    static class StubResponse implements Response {

        private final String body

        StubResponse(String body) {
            this.body = body
        }

        @Override
        int getStatusCode() {
            200
        }

        @Override
        Map<String, List<String>> getHeaders() {
            [:]
        }

        @Override
        InputStream getBody() {
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
        }

        @Override
        void close() {
        }
    }
}
