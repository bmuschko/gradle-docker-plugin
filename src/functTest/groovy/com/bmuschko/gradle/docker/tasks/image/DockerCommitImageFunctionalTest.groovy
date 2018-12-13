package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerCommitImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can commit image"() {
        given:
        String commitImage = """
            task commitImage(type: DockerCommitImage) {
                dependsOn startContainer
                targetContainerId createContainer.getContainerId()
                tag = "myimage:latest"
            }
        """

        buildFile << containerStart(commitImage)

        when:
        BuildResult result = build('commitImage')

        then:
        result.output.contains("Commiting image for container")
    }

    static String containerStart(containerCommitImageExecutionTask) {
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task pullImage(type: DockerPullImage) {
                repository = '$TEST_IMAGE'
                tag = '$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImageId()
                autoRemove = true
                entrypoint = ['tail', '-f', '/dev/null']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            ${containerCommitImageExecutionTask}
        """
    }
}
