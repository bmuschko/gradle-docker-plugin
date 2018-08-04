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

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerCreateContainerFunctionalTest extends AbstractFunctionalTest {

    def "can override default MAC address"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['ifconfig']
                macAddress = '02:03:04:05:06:07'
                cpuset = '1'
                labels = ["project.name": "\$project.name"]
            }
        """
        buildFile << containerUsage(containerCreationTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("HWaddr 02:03:04:05:06:07")
    }

    def "can set multiple environment variables"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['env']
                
                // deprecated, use the below examples
                env = ['HELLO=WORLD']
                
                // add by appending new map to current map
                envVars << ['one' : 'two', 'three' : 'four']
                
                // add by calling helper method N number of times
                withEnvVar('five', 'six')
                withEnvVar({'seven'}, 'eight')
                withEnvVar('nine', {'ten'})
            }
        """
        buildFile << containerUsage(containerCreationTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("HELLO=WORLD")
        result.output.contains("one=two")
        result.output.contains("three=four")
        result.output.contains("five=six")
        result.output.contains("seven=eight")
        result.output.contains("nine=ten")
    }

    def "with autoRemove set, the container is already being removed after stopping"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                autoRemove = true
            }
        """
        buildFile << containerUsage(containerCreationTask)

        when:
        BuildResult result = buildAndFail('logContainer')

        then:
        result.output.matches(/(?ms).*removal of container \w+ is already in progress.*/)
    }

    static String containerUsage(String containerCreationTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            ${containerCreationTask}

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }
        """
    }
}
