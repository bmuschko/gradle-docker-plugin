package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerLivenessFunctionalTest extends AbstractFunctionalTest {

    def "Can start a container and probe it for liveness"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
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
                livenessProbe(300000, 30000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
                doLast {
                    println 'doLast container state is ' + lastInspection()
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'livenessProbe'
                targetContainerId { startContainer.getContainerId() }
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 137]
                timeout = 60000
                execStopProbe(60000, 10000)
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
        result.output.contains('doLast container state is')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can start a container and use probe task but not define a probe"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
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
                onComplete {
                    println 'Container is now in a running state'
                }
                doLast {
                    println 'doLast container state is ' + lastInspection()
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'livenessProbe'
                targetContainerId { startContainer.getContainerId() }
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 1, 137]
                timeout = 60000
                execStopProbe(60000, 10000)
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
        result.output.contains('doLast container state is')
        result.output.contains('Container is now in a running state')
        result.output.contains('Container has been exec-stopped')
    }

    def "Probe will fail if container is not running"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer

            task pullImage(type: DockerPullImage) {
                repository = 'postgres'
                tag = 'alpine'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
            }
            
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn 'createContainer'
                targetContainerId { createContainer.getContainerId() }
                livenessProbe(300000, 30000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live...'
                }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn livenessProbe
                finalizedBy removeContainer
            }
        """

        expect:
        BuildResult result = buildAndFail('workflow')
        result.output.contains('Starting liveness')
        result.output.contains("is not running and so can't perform liveness")
    }
}
