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

class DockerExecContainerFunctionalTest extends AbstractFunctionalTest {

    def "Execute command within running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World')
    }

    def "Execute multiple commands within running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                withCommand(['echo', 'Hello World One'])
                withCommand(['echo', 'Hello World Two'])
                withCommand(['echo', 'Hello World Three'])
                doLast {
                    logger.quiet "FOUND EXEC-IDS: " + getExecIds().size()
                }
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World One')
        result.output.contains('Hello World Two')
        result.output.contains('Hello World Three')
        result.output.contains('FOUND EXEC-IDS: 3')
    }


    def "Execute command within running container and not specify cmd arg"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World')
    }

    def "Execute command within stopped container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = buildAndFail('execContainer')

        then:
        result.task(':execContainer').outcome == TaskOutcome.FAILED
        result.output.contains('is not running')
    }

    def "Execute command as a non-root user within a running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['sh', '-c', 'id -u && id -g']
                user = '10000:10001'
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('execContainer')

        then:
        result.output.contains('10000\n10001')
    }

    def "Fail if exitCode is not within allowed bounds"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['test', '-e', '/not_existing_file']
                successOnExitCodes = [0]
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = buildAndFail('execContainer')

        then:
        result.task(':execContainer').outcome == TaskOutcome.FAILED
        result.output.contains('is not a successful exit code.')
    }

    def "Execute command and define a probe"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                withCommand(['sleep', '10'])
                successOnExitCodes = [0]
                execProbe(15000, 1000)
                onComplete {
                    logger.quiet 'Finished Probing Exec'
                }
            }
        """
        buildFile << containerUsage(containerExecutionTask, 15)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Finished Probing Exec')
    }

    static String containerUsage(String containerExecutionTask, int sleep = 10) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','$sleep']
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

            ${containerExecutionTask}

            task logContainer(type: DockerLogsContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
            }
        """
    }
}
