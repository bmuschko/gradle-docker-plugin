package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

/**
 * System tests for {@link com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer} class.
 */
class DockerInspectExecContainerFunctionalTest extends AbstractFunctionalTest {

    def "Inspect executed command within running container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$AbstractFunctionalTest.TEST_IMAGE'
                tag = '$AbstractFunctionalTest.TEST_IMAGE_TAG'
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

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['touch', '/tmp/test.txt']
            }
            
            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                targetExecId { execContainer.execId }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn inspectExec
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Exec ID: ")
    }

    def "Inspect exit code of executed command within running container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = '$AbstractFunctionalTest.TEST_IMAGE'
                tag = '$AbstractFunctionalTest.TEST_IMAGE_TAG'
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

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['test', '-e', '/not_existing_file']
            }
            
            task inspectExec(type: DockerInspectExecContainer) {
                dependsOn execContainer
                targetExecId { execContainer.execId }
                onNext { r ->
                    if(r.exitCode) {
                        throw new GradleException("Docker container exec failed with exit code: " + r.exitCode)
                    }
                }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn inspectExec
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        BuildResult result = buildAndFail('workflow')
        result.output.contains("Docker container exec failed with exit code: ")
    }
}
