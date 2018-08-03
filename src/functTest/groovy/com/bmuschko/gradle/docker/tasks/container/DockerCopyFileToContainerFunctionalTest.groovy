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
import org.gradle.testkit.runner.TaskOutcome

class DockerCopyFileToContainerFunctionalTest extends AbstractFunctionalTest {

    def "can copy a file into a container"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/HelloWorld.txt"
                remotePath = "/root"
            }
        """

        expect:
        build('copyFileIntoContainer')
    }

    def "can copy multiple files into a container"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                withFile("$projectDir/HelloWorld.txt", '/root')
                withFile("$projectDir/HelloWorld.txt", '/tmp')
                withFile({ "$projectDir/HelloWorld.txt" }, { '/' })
            }
        """

        expect:
        build('copyFileIntoContainer')
    }

    def "can copy a TAR file into a container"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << tarTask()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer, createTarFile
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                tarFile { new File("$projectDir/HelloWorld.tgz") }
                remotePath = "/root"
            }
        """

        expect:
        build('copyFileIntoContainer')
    }

    def "can copy multiple TAR files into a container"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << tarTask()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer, createTarFile
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                withTarFile({ new File("$projectDir/HelloWorld.tgz") }, '/root')
                withTarFile({ new File("$projectDir/HelloWorld.tgz") }, {'/'} )
            }
        """

        expect:
        build('copyFileIntoContainer')
    }

    def "fails task execution if properties hostPath and tarFile are both specified"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/HelloWorld.txt"
                tarFile { new File("$projectDir/HelloWorld.txt") }
                remotePath = "/root"
            }
        """

        when:
        BuildResult result = buildAndFail('copyFileIntoContainer')

        then:
        result.task(':copyFileIntoContainer').outcome == TaskOutcome.FAILED
        result.output.contains('Can specify either hostPath or tarFile not both')
    }

    private void writeHelloWorldFile() {
        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') {
            it.write('Hello, World!')
        }
    }

    static String commonBuildScriptTasks() {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$AbstractFunctionalTest.TEST_IMAGE'
                tag = '$AbstractFunctionalTest.TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['echo', 'Hello World']
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }
        """
    }

    private String tarTask() {
        """
            task createTarFile(type: Tar) {
                from "$projectDir/HelloWorld.txt"
                baseName = 'HelloWorld'
                destinationDir = projectDir
                extension = 'tgz'
                compression = Compression.GZIP
            }
        """
    }
}
