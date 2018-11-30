package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerLivenessFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Can start a container and probe it for liveness"() {
        given:
        String additionalTasks = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                livenessProbe(300000, 30000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
                doLast {
                    println 'doLast container state is ' + lastInspection()
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn livenessProbe
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 137]
                awaitStatusTimeout = 60000
                execStopProbe(60000, 10000)
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }
        """
        buildFile << containerUsage(additionalTasks)

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('doLast container state is')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can start a container and use probe task but not define a probe"() {
        given:
        String additionalTasks = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                onComplete {
                    println 'Container is now in a running state'
                }
                doLast {
                    println 'doLast container state is ' + lastInspection()
                }
            }
            
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn livenessProbe
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                cmd = ['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"]
                successOnExitCodes = [0, 1, 137]
                awaitStatusTimeout = 60000
                execStopProbe(60000, 10000)
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }
        """
        buildFile << containerUsage(additionalTasks)

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('doLast container state is')
        result.output.contains('Container is now in a running state')
        result.output.contains('Container has been exec-stopped')
    }

    def "Probe will fail if container is not running"() {
        String additionalTasks = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                livenessProbe(300000, 30000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live...'
                }
            }
        """
        buildFile << containerUsage(additionalTasks)

        when:
        BuildResult result = buildAndFail('livenessProbe')

        then:
        result.output.contains('Starting liveness')
        result.output.contains("is not running and so can't perform liveness")
    }

    static String containerUsage(String additionalTasks) {
        """
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
                targetImageId pullImage.getImageId()
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
            
            ${additionalTasks}
        """
    }
}
