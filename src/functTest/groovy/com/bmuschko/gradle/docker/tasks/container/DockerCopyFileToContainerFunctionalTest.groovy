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

import static com.bmuschko.gradle.docker.TextUtils.escapeFilePath

class DockerCopyFileToContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can copy a file into a container"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}"
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
                targetContainerId createContainer.getContainerId()
                withFile("${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}", '/root')
                withFile("${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}", '/tmp')
                withFile({ "${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}" }, { '/' })
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
                targetContainerId createContainer.getContainerId()
                tarFile = new File("${escapeFilePath(projectDir)}", 'HelloWorld.tgz')
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
                targetContainerId createContainer.getContainerId()
                withTarFile({ new File("${escapeFilePath(projectDir)}", 'HelloWorld.tgz') }, '/root')
                withTarFile({ new File("${escapeFilePath(projectDir)}", 'HelloWorld.tgz') }, {'/'} )
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
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}"
                tarFile = new File("${escapeFilePath(projectDir)}", 'HelloWorld.txt')
                remotePath = "/root"
            }
        """

        when:
        BuildResult result = buildAndFail('copyFileIntoContainer')

        then:
        result.task(':copyFileIntoContainer').outcome == TaskOutcome.FAILED
        result.output.contains('Can specify either hostPath or tarFile not both')
    }

    def "can copy a file into a container with configuration cache"() {
        given:
        writeHelloWorldFile()
        buildFile << commonBuildScriptTasks()
        buildFile << """
            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}"
                remotePath = "/root"
            }
        """

        when:
        BuildResult result = build('copyFileIntoContainer')

        then:
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build('copyFileIntoContainer')

        then:
        result.output.contains("Configuration cache entry reused.")
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
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['echo', 'Hello World']
            }

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId createContainer.getContainerId()
            }
        """
    }

    private String tarTask() {
        """
            task createTarFile(type: Tar) {
                from "${escapeFilePath(new File(projectDir, 'HelloWorld.txt'))}"
                baseName = 'HelloWorld'
                destinationDir = projectDir
                extension = 'tgz'
                compression = Compression.GZIP
            }
        """
    }
}
