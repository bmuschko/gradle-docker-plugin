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

class DockerCreateContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can override default MAC address"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImageId()
                cmd = ['ifconfig']
                macAddress = '02:03:04:05:06:07'
                cpuset = '1'
                labels = ["project.name": "\$project.name"]
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            containerLogAndRemove()

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
                targetImageId pullImage.getImageId()
                cmd = ['env']
                
                // deprecated, use the below examples
                env = ['HELLO=WORLD']
                
                // add by appending new map to current map
                envVars.set(['one' : 'two', 'three' : 'four'])
                
                // add by calling helper method N number of times
                withEnvVar('five', 'six')
                withEnvVar({'seven'}, 'eight')
                withEnvVar('nine', {'ten'})
            }
        """
        buildFile << 
            containerStart(containerCreationTask) <<
            containerLogAndRemove()

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

    def "with autoRemove set, the container is removed after stopping"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImageId()
                autoRemove = true
            }
        """

        String containerInspect = """
            task inspectStoppedContainer(type: DockerInspectContainer) {
                dependsOn stopContainer
                targetContainerId startContainer.getContainerId()

                onError { err -> println 'RESULT: ' + err }
            }
        """

        buildFile <<
            containerStart(containerCreationTask) <<
            containerStop() <<
            containerInspect

        when:
        BuildResult result = build('inspectStoppedContainer')

        then:
        result.output.contains(
            'RESULT: com.github.dockerjava.api.exception.NotFoundException')
    }

    def "without autoRemove set, the container still exists after stopping"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImageId()
            }
        """

        String containerInspect = """
            task inspectStoppedContainer(type: DockerInspectContainer) {
                dependsOn stopContainer
                targetContainerId startContainer.getContainerId()
            }
        """

        buildFile <<
            containerStart(containerCreationTask) <<
            containerStop() <<
            containerInspect

        when:
        build('inspectStoppedContainer')

        then:
        notThrown(Exception)
    }


    static String containerStart(String containerCreationTask) {
        // Starts with the union of all needed imports.
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            ${containerCreationTask}

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
        """
    }

    static String containerLogAndRemove() {
        """
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
            }

            task logContainer(type: DockerLogsContainer) {
                dependsOn inspectContainer
                finalizedBy removeContainer
                targetContainerId inspectContainer.getContainerId()
                follow = true
                tailAll = true
            }
        """
    }

    static String containerStop() {
        """
            task stopContainer(type: DockerStopContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
            }
        """
    }
}
