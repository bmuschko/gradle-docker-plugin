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
import spock.lang.Ignore

class DockerExecContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Execute command within running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['echo', 'Hello World'] as String[])
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World')
    }

    @Ignore("Flaky test that fails with org.apache.hc.core5.http.StreamClosedException: Stream already closed")
    def "Execute multiple commands within running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['echo', 'Hello World One'] as String[])
                commands.add(['echo', 'Hello World Two'] as String[])
                commands.add(['echo', 'Hello World Three'] as String[])
                doLast {
                    logger.quiet "FOUND EXEC-IDS: " + execIds.get().size()
                }
            }
        """
        buildFile << containerUsage(containerExecutionTask, 120)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World One')
        result.output.contains('Hello World Two')
        result.output.contains('Hello World Three')
        result.output.contains('FOUND EXEC-IDS: 3')
    }

    def "Execute command within stopped container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                commands.add(['echo', 'Hello World'] as String[])
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
                targetContainerId startContainer.getContainerId()
                commands.add(['sh', '-c', 'id -u && id -g'] as String[])
                user = '10000:10001'
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('execContainer')

        then:
        result.output.contains('10000\n10001')
    }

    def "Execute command with custom working directory within a running container"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['pwd'] as String[])
                workingDir = '/usr/local/bin/'
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('execContainer')

        then:
        result.output.contains('/usr/local/bin')
    }

    def "Fail if exitCode is not within allowed bounds"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['test', '-e', '/not_existing_file'] as String[])
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
                targetContainerId startContainer.getContainerId()
                commands.add(['sleep', '10'] as String[])
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

    def "Execute command with configuration cache enabled"() {
        given:
        String containerExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['echo', 'Hello World'] as String[])
            }
        """
        buildFile << containerUsage(containerExecutionTask)

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains('Hello World')
        result.output.contains("0 problems were found storing the configuration cache.")
    }

    static String containerUsage(String containerExecutionTask, int sleep = 30) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['sleep','$sleep']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }

            ${containerExecutionTask}

            task logContainer(type: DockerLogsContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                follow = true
                tailAll = true
            }
        """
    }
}
