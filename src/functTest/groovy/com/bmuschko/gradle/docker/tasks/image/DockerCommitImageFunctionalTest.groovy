package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerCommitImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String COMMIT_TASK_NAME = 'commitImage'

    def "can commit image"() {
        given:
        String commitImage = """
            task $COMMIT_TASK_NAME(type: DockerCommitImage) {
                dependsOn startContainer
                targetContainerId createContainer.getContainerId()
                author = "john doe"
                message = "My image created"
                repository = "myimage"
                tag = "latest"
            }
        """

        buildFile << containerStart(commitImage)
        buildFile << """
            commitImage.finalizedBy removeImage
        """

        when:
        BuildResult result = build(COMMIT_TASK_NAME)

        then:
        result.output.contains("Committing image 'myimage:latest' for container")
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build(COMMIT_TASK_NAME)

        then:
        result.output.contains("Configuration cache entry reused.")
    }

    def "cannot commit image with invalid container"() {
        given:
        String commitImage = """
            task $COMMIT_TASK_NAME(type: DockerCommitImage) {
                dependsOn startContainer
                targetContainerId "idonotexist"
                repository = "myimage"
                tag = "latest"
            }
        """

        buildFile << containerStart(commitImage)

        when:
        BuildResult result = buildAndFail(COMMIT_TASK_NAME)

        then:
        result.output.contains('No such container: idonotexist')
    }

    static String containerStart(containerCommitImageExecutionTask) {
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                hostConfig.autoRemove = true
                entrypoint = ['tail', '-f', '/dev/null']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            ${containerCommitImageExecutionTask}

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId commitImage.getImageId()
            }

            commitImage.finalizedBy removeContainer
        """
    }
}
