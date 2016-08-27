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
class DockerWaitContainerFunctionalTest extends AbstractFunctionalTest {
    def "Wait for a container to finish and get the exit code"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task createContainer(type: DockerCreateContainer){
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd 'sh', '-c', 'exit 0'
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId {createContainer.getContainerId()}
            }

            task runContainers(type: DockerWaitContainer){
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
                doLast{
                    if(getExitCode() != 0) {
                        println "Container failed with exit code \${getExitCode()}"
                    } else {
                        println "Container successful"
                    }
                }
            }
        """

        expect:
        BuildResult result = build('runContainers')
        result.output.contains("Container successful")
    }

    def "Wait for a container to fail and get the exit code"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task createContainer(type: DockerCreateContainer){
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd 'sh', '-c', 'exit 1'
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId {createContainer.getContainerId()}
            }

            task runContainers(type: DockerWaitContainer){
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
                doLast{
                    if(getExitCode() != 0) {
                        println "Container failed with exit code \${getExitCode()}"
                    } else {
                        println "Container successful"
                    }
                }
            }
        """

        expect:
        BuildResult result = build('runContainers')
        result.output.contains("Container failed with exit code 1")
    }

    def "Wait for a container for a defined timeout"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task createContainer(type: DockerCreateContainer){
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd 'sh', '-c', 'sleep 15'
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId {createContainer.getContainerId()}
            }

            task runContainers(type: DockerWaitContainer){
                timeout = 1
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
            }
        """

        expect:
        BuildResult result = buildAndFail('runContainers')
        result.output.contains("Awaiting status code timeout")
    }
}
