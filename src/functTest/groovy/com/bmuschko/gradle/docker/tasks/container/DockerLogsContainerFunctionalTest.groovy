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

class DockerLogsContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Can start a container and watch logs"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output =~ /.*[\n\r]Hello World[\n\r]  indent[\n\r].*/
    }

    def "Can limit container logs by the since parameter"() {
        given:
        String logContainerTask = """
             task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                since = new Date()+1
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        !result.output.contains("Hello World")
    }

    def "can capture all output with tailAll option"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("Hello World")
    }

    def "Cannot specify the properties tailAll and tailCount together"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
                tailCount = 20
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = buildAndFail('logContainer')

        then:
        result.task(':logContainer').outcome == TaskOutcome.FAILED
    }

    def "Setting tailCount to 0 should produce no output"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                tailCount = 0
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        !result.output.contains("Hello World")
    }

    def "Setting tailCount to 1 should produce output"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                tailCount = 1
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("  indent")
    }

    def "Can render timestamps in output"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                tailAll = true
                showTimestamps = true
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output ==~ ~/(?s).*[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9][.][0-9]+Z\s+Hello World.*/
    }

    def "Can write output to file"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { startContainer.getContainerId() }
                sink = project.file("${projectDir.path}/log-sink.txt").newWriter()
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        build('logContainer')

        then:
        File outputFile = new File("${projectDir.path}/log-sink.txt")
        outputFile.exists() && outputFile.text.contains("Hello World")
    }

    def "Prints an error message when obtaining logs fails"() {
        given:
        String logContainerTask = """
            task logContainer(type: DockerLogsContainer) {
                targetContainerId { "not_existing_container" }
            }
        """
        buildFile << containerUsage(logContainerTask)

        when:
        BuildResult result = buildAndFail('logContainer')

        then:
        result.task(':logContainer').outcome == TaskOutcome.FAILED
        result.output.contains("No such container: not_existing_container")
    }

    static String containerUsage(String logContainerTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['/bin/sh','-c','echo -e "Hello World\\n  indent"']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            ${logContainerTask}

            logContainer {
                dependsOn startContainer
                finalizedBy removeContainer
            }
        """
    }
}

