/*
 * Copyright 2014 the original author or authors.
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
package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.TestPrecondition
import com.bmuschko.gradle.docker.tasks.DockerTaskIntegrationTest
import org.gradle.api.Task
import spock.lang.Requires

class DockerCreateContainerIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('createContainer', type: DockerCreateContainer) {
            imageId = 'busybox'
        }
    }

    @Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
    def "Set custom property values"() {
        when:
        DockerCreateContainer task = createAndConfigureTask()
        task.exposedPorts = ['TCP': 80]
        task.volumes = ['/my/path']
        task.binds = ['/my/local/path': '/my/path']
        task.execute()

        then:
        task.containerId
    }

    @Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
    def "Publish all ports"() {
        when:
        DockerCreateContainer task = createAndConfigureTask()
        task.publishAll = true
        task.execute()

        then:
        task.containerId
    }
}
