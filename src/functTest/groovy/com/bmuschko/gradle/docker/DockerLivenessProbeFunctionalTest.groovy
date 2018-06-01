package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerLivenessProbeFunctionalTest extends AbstractFunctionalTest {

    def "Can start a container and probe it for liveness"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer
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
            
            task livenessProbe(type: DockerLivenessProbeContainer) {
                dependsOn 'startContainer'
                targetContainerId { startContainer.getContainerId() }
                probe(300000, 30000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live...'
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn 'livenessProbe'
                targetContainerId { startContainer.getContainerId() }
                //cmd = ['su', 'postgres', "-c '/usr/local/bin/pg_ctl stop -m fast'"]
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]

                timeout = 60000
                probe(60000, 10000)
                onComplete {
                    println 'Container has been exec/stopped...'
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
        result.output.contains('Starting liveness probe on container')
        result.output.contains('Container is now live')
        result.output.contains('Monkey')
    }

    def "Probe will fail if container is not running"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessProbeContainer

            task pullImage(type: DockerPullImage) {
                repository = 'postgres'
                tag = 'alpine'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
            }
            
            task livenessProbe(type: DockerLivenessProbeContainer) {
                dependsOn 'createContainer'
                targetContainerId { createContainer.getContainerId() }
                probe(300000, 30000, 'database system is ready to accept connections')
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
        result.output.contains('Starting liveness probe on container')
        result.output.contains("is not running and so can't perform liveness probe")
    }
}
