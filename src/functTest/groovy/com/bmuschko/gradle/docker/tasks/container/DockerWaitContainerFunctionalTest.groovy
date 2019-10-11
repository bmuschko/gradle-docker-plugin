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

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerWaitContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Wait for a container to finish and get the exit code"() {
        given:
        String containerCmd = "'sh', '-c', 'exit 0'"
        String waitOnContainerTask = """
            task waitOnContainer(type: DockerWaitContainer){
                targetContainerId startContainer.getContainerId()
                doLast{
                    if(getExitCode() != 0) {
                        println "Container failed with exit code \${getExitCode()}"
                    } else {
                        println "Container successful"
                    }
                }
            }
        """
        buildFile << containerUsage(containerCmd, waitOnContainerTask)

        when:
        BuildResult result = build('waitOnContainer')

        then:
        result.output.contains('Container successful')
    }

    def "Wait for a container to fail and get the exit code"() {
        given:
        String containerCmd = "'sh', '-c', 'exit 1'"
        String waitOnContainerTask = """
            task waitOnContainer(type: DockerWaitContainer){
                targetContainerId startContainer.getContainerId()
                doLast{
                    if(getExitCode() != 0) {
                        println "Container failed with exit code \${getExitCode()}"
                    } else {
                        println "Container successful"
                    }
                }
            }
        """
        buildFile << containerUsage(containerCmd, waitOnContainerTask)

        when:
        BuildResult result = build('waitOnContainer')

        then:
        result.output.contains("Container failed with exit code 1")
    }

    def "Wait for a container for a defined timeout"() {
        given:
        String containerCmd = "'sh', '-c', 'sleep 15'"
        String waitOnContainerTask = """
            task waitOnContainer(type: DockerWaitContainer){
                awaitStatusTimeout = 1
                targetContainerId startContainer.getContainerId()
            }
        """
        buildFile << containerUsage(containerCmd, waitOnContainerTask)

        when:
        BuildResult result = buildAndFail('waitOnContainer')

        then:
        result.output.contains("Awaiting status code timeout")
    }

    static String containerUsage(String containerCommand, String waitOnContainerTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer){
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd.addAll($containerCommand)
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId createContainer.getContainerId()
                force = true
            }

            ${waitOnContainerTask}

            waitOnContainer {
                dependsOn startContainer
                finalizedBy removeContainer
            }
        """
    }
}
