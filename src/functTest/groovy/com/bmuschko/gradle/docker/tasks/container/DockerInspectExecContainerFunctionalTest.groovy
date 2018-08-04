package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

/**
 * System tests for {@link com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer} class.
 */
class DockerInspectExecContainerFunctionalTest extends AbstractFunctionalTest {

    def "Inspect executed command within running container"() {
        given:
        String containerInspectExecutionTask = """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['touch', '/tmp/test.txt']
            }
            
            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetExecId { execContainer.execId }
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
                targetContainerId { startContainer.getContainerId() }
                cmd = ['test', '-e', '/not_existing_file']
            }

            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetExecId { execContainer.execId }
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
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','10']
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

            ${containerExecInspectExecutionTask}
        """
    }
}
