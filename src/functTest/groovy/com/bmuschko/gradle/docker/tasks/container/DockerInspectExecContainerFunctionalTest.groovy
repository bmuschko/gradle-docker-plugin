package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

/**
 * System tests for {@link com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer} class.
 */
class DockerInspectExecContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Inspect executed command within running container"() {
        given:
        String containerInspectExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['touch', '/tmp/test.txt'] as String[])
            }
            
            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetExecId { execContainer.execIds.get()[0] }
            }
        """
        buildFile << containerUsage(containerInspectExecutionTask)

        when:
        BuildResult result = build('inspectExec')

        then:
        result.output.contains('Exec ID: ')
    }

    def "Inspect exit code of executed command within running container"() {
        given:
        String containerInspectExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['test', '-e', '/not_existing_file'] as String[])
            }

            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetExecId { execContainer.execIds.get()[0] }
                onNext { r ->
                    if(r.exitCode) {
                        throw new GradleException("Docker container exec failed with exit code: " + r.exitCode)
                    }
                }
            }
        """
        buildFile << containerUsage(containerInspectExecutionTask)

        when:
        BuildResult result = buildAndFail('inspectExec')

        then:
        result.output.contains('Docker container exec failed with exit code: ')
    }

    static String containerUsage(String containerExecInspectExecutionTask) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['sleep','10']
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

            ${containerExecInspectExecutionTask}
        """
    }
}
