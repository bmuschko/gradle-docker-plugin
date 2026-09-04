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
package com.bmuschko.gradle.docker.internal.services

import com.github.dockerjava.core.DefaultDockerClientConfig
import spock.lang.Specification

class DockerClientKeyTest extends Specification {

    def "clients are shared when the configuration and the BuildKit setting match"() {
        given:
        DockerClientKey key = new DockerClientKey(configuration('tcp://127.0.0.1:2375'), true)
        DockerClientKey same = new DockerClientKey(configuration('tcp://127.0.0.1:2375'), true)

        expect:
        key == same
        key.hashCode() == same.hashCode()
    }

    def "a BuildKit client is never shared with a classic builder client"() {
        given:
        DockerClientKey buildKit = new DockerClientKey(configuration('tcp://127.0.0.1:2375'), true)
        DockerClientKey classic = new DockerClientKey(configuration('tcp://127.0.0.1:2375'), false)

        expect:
        buildKit != classic
    }

    def "clients are not shared between different daemons"() {
        given:
        DockerClientKey one = new DockerClientKey(configuration('tcp://127.0.0.1:2375'), true)
        DockerClientKey other = new DockerClientKey(configuration('tcp://127.0.0.1:2376'), true)

        expect:
        one != other
    }

    private static DefaultDockerClientConfig configuration(String dockerHost) {
        DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerTlsVerify(false)
                .build()
    }
}
