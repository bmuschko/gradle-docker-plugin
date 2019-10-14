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
import org.gradle.testkit.runner.TaskOutcome

class DockerRenameContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Create a container and rename it"() {
        given:
        String uniqueContainerName = createUniqueContainerName()
        String renameContainerTask = """
            task renameContainer(type: DockerRenameContainer) {
                targetContainerId createContainer.getContainerId()
                renameTo = "$uniqueContainerName"
            }
        """
        buildFile << containerUsage(renameContainerTask)

        expect:
        build('renameContainer')
    }

    def "Create a container and rename it with incorrect source targetContainerId"() {
        given:
        String randomName = createUniqueContainerName()
        String uniqueContainerName = createUniqueContainerName()
        String renameContainerTask = """
            task renameContainer(type: DockerRenameContainer) {
                targetContainerId "$randomName"
                renameTo = "$uniqueContainerName"
            }
        """
        buildFile << containerUsage(renameContainerTask)

        when:
        BuildResult result = buildAndFail('renameContainer')

        then:
        result.task(':renameContainer').outcome == TaskOutcome.FAILED
    }

    static String containerUsage(String renameContainerTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
    
            task pullImage(type: DockerPullImage) {
                image = '$AbstractGroovyDslFunctionalTest.TEST_IMAGE:$AbstractGroovyDslFunctionalTest.TEST_IMAGE_TAG'
            }
    
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
            }
    
            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId createContainer.getContainerId()
                force = true
            }
    
            ${renameContainerTask}
    
            renameContainer {
                dependsOn createContainer
                finalizedBy removeContainer
            }
        """
    }
}
