package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerSaveImageFunctionalTest extends AbstractFunctionalTest {

    final String ImageId = createUniqueImageId()

    def setup() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tag = "${ImageId}"
                labels = ["setup":"${ImageId}"]
            }
        """

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains("Created image with ID")
    }

    def "Can docker image be saved without compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn dockerfile
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("build/docker/${ImageId}-docker-image.tar")
            }
        """

        when:
        build('saveImage')

        then:
        noExceptionThrown()
    }
}
