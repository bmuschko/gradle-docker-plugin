package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerExecStopFunctionalTest extends AbstractFunctionalTest {

    def "Can start a container and then successfully exec-stop it"() {
        given:
        String containerExecutionStopTask = """
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn livenessProbe
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 137]
                timeout = 60000
                execStopProbe(60000, 5000)
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }
        """
        buildFile << containerUsage(containerExecutionStopTask)

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can start a container and exec-stop it with no cmd args"() {
        given:
        String containerExecutionStopTask = """
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn livenessProbe
                finalizedBy removeContainer
                targetContainerId { startContainer.getContainerId() }
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }
        """
        buildFile << containerUsage(containerExecutionStopTask)

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can  exec-stop a created container with no cmd args and catch normal DockerStopContainer exception"() {
        given:
        String containerExecutionStopTask = """
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                onError { exc ->
                    logger.quiet "Found exception: " + exc.class.simpleName
                }
            }
        """
        buildFile << containerUsage(containerExecutionStopTask)

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Found exception: NotModifiedException')
    }

    static String containerUsage(String containerExecutionStopTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer

            task pullImage(type: DockerPullImage) {
                repository = 'postgres'
                tag = 'alpine'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
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
            
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                livenessProbe(60000, 5000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
            }
            
            ${containerExecutionStopTask}
        """
    }
}
