package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class DockerLivenessFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Can start a container and probe it for liveness"() {
        given:
        String livenessContainerTask = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                livenessProbe(300000, 5000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live'
                }
            }
        """
        buildFile << containerUsage()
        buildFile << livenessContainerTask
        buildFile << execStopContainerTask([0, 137])

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('Container is now live')
        result.output.contains('Container has been exec-stopped')
    }

    def "Can start a container and use probe task but not define a probe"() {
        given:
        String livenessContainerTask = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                onComplete {
                    println 'Container is now in a running state'
                }
            }
        """
        buildFile << containerUsage()
        buildFile << livenessContainerTask
        buildFile << execStopContainerTask([0, 1, 137])

        when:
        BuildResult result = build('execStopContainer')

        then:
        result.output.contains('Starting liveness')
        result.output.contains('Container is now in a running state')
        result.output.contains('Container has been exec-stopped')
    }

    def "Probe will fail if container is not running"() {
        String livenessContainerTask = """
            task livenessProbe(type: DockerLivenessContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                livenessProbe(300000, 5000, 'database system is ready to accept connections')
                onComplete {
                    println 'Container is now live...'
                }
            }
        """
        buildFile << containerUsage()
        buildFile << livenessContainerTask

        when:
        BuildResult result = buildAndFail('livenessProbe')

        then:
        result.output.contains('Starting liveness')
        result.output.contains("is not running")
    }

    @Unroll
    def "Probe cannot configure all fields from DockerLogsContainer"() {
        String livenessContainerTask = """
            task livenessProbe(type: DockerLivenessContainer) {
                $property = $value
            }
        """
        buildFile << containerUsage()
        buildFile << livenessContainerTask

        when:
        BuildResult result = buildAndFail('livenessProbe')

        then:
        result.output.contains("The value for this property is final and cannot be changed any further.")

        where:
        property    | value
        'tailAll'   | false
        'tailCount' | 10
        'follow'    | true
    }

    static String containerUsage() {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerLivenessContainer
            import com.bmuschko.gradle.docker.tasks.container.extras.DockerExecStopContainer

            task pullImage(type: DockerPullImage) {
                image = 'postgres:9.6.15-alpine'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
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
        """
    }

    static String execStopContainerTask(List<Integer> successOnExitCodes) {
        """
            task execStopContainer(type: DockerExecStopContainer) {
                dependsOn livenessProbe
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                withCommand(['su', 'postgres', "-c", "/usr/local/bin/pg_ctl stop -m fast"])
                successOnExitCodes = [${successOnExitCodes.join(', ')}]
                awaitStatusTimeout = 60000
                execStopProbe(60000, 5000)
                onComplete {
                    println 'Container has been exec-stopped'
                }
            }
        """
    }
}
