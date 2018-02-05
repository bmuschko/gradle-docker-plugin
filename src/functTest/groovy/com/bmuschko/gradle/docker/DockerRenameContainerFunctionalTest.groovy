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

class DockerRenameContainerFunctionalTest extends AbstractFunctionalTest {

    def "Create a container and rename it"() {

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
            }

            task renameContainer(type: DockerRenameContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                renameTo = "$uniqueContainerName"
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { "$uniqueContainerName" }
                force = true
            }

            task workflow {
                dependsOn renameContainer
                finalizedBy removeContainer
            }
        """

        expect:
        build('workflow')
    }

    def "Create a container and rename it with incorrect source targetContainerId"() {

        String randomName = createUniqueContainerName()
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
            }

            task renameContainer(type: DockerRenameContainer) {
                dependsOn createContainer
                targetContainerId { "$randomName" }
                renameTo = "$uniqueContainerName"
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { createContainer.getContainerId() }
                force = true
            }

            task workflow {
                dependsOn renameContainer
                finalizedBy removeContainer
            }
        """

        expect:
        buildAndFail('workflow')
    }
}
