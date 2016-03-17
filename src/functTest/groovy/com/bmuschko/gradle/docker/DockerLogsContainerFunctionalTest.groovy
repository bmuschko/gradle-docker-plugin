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
package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerLogsContainerFunctionalTest extends AbstractFunctionalTest {
    def setup() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

            task pullImage(type: DockerPullImage) {
                repository = 'busybox'
                tag = 'latest'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.repository+":"+pullImage.tag }
                cmd = ['/bin/sh','-c',"echo Hello World"]
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

           task removeContainer(type: DockerRemoveContainer) {
                dependsOn 'logContainer'
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

    }

    def "Can start a container and watch logs"() {
        buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Hello World")
    }

    def "Container logs are limited by the since parameter"() {
        buildFile << """
             task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                since = new Date()+1
            }
        """

        expect:
        BuildResult result = build('workflow')
        !result.output.contains("Hello World")
    }

    def "tailAll = true should have all output"() {
        buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Hello World")
    }

    def "tailAll and tailCount cannot be specified together"() {
       buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
                tailCount = 20
            }
        """

        expect:
        BuildResult result = buildAndFail('workflow')
    }

    def "tailCount = 0 should have no output"() {
        buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                tailCount = 0
            }
        """

        expect:
        BuildResult result = build('workflow')
        !result.output.contains("Hello World")
    }

    def "tailCount = 1 should have output"() {
        buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                tailCount = 1
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Hello World")
    }

    def "showTimestamps works"() {
        buildFile << """
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
                showTimestamps = true
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output ==~ ~/(?s).*[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9][.][0-9]+Z\s+Hello World.*/
    }
}

