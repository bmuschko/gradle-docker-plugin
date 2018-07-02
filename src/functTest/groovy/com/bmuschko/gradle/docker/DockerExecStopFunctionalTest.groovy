package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerExecStopFunctionalTest extends AbstractFunctionalTest {

    def "Can start a container and then successfully exec-stop it"() {
        buildFile << """
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
            
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn 'startContainer'
                targetContainerId { startContainer.getContainerId() }
                livenessProbe(60000, 5000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'livenessProbe'
                targetContainerId { startContainer.getContainerId() }
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 137]
                timeout = 60000
                execStopProbe(60000, 5000)
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn execStopContainer
                finalizedBy removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains('Starting liveness')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can start a container and exec-stop it with no cmd args"() {
        buildFile << """
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
            
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn 'startContainer'
                targetContainerId { startContainer.getContainerId() }
                livenessProbe(60000, 5000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'livenessProbe'
                targetContainerId { startContainer.getContainerId() }
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn execStopContainer
                finalizedBy removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains('Starting liveness')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can  exec-stop a created container with no cmd args and catch normal DockerStopContainer exception"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer

            task pullImage(type: DockerPullImage) {
                repository = 'postgres'
                tag = 'alpine'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'createContainer'
                targetContainerId { createContainer.getContainerId() }
                onError { exc ->
                    logger.quiet "Found exception: " + exc.class.simpleName
                }
            }

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn execStopContainer
                finalizedBy removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains('Found exception: NotModifiedException')
    }
}
