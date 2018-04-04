package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.utils.IOUtils
import org.gradle.testkit.runner.BuildResult
import spock.lang.Stepwise

import java.util.zip.GZIPInputStream

@Stepwise
class DockerSaveImageFunctionalTest extends AbstractFunctionalTest {

    final String ImageId = createUniqueImageId()
    final String controlSavedImage = "build/docker/${ImageId}-docker-image-control.tar"
    final String imageFile = "build/docker/${ImageId}-docker-image.tar"
    final String compressedImageFile = "build/docker/${ImageId}-compressed-docker-image.tar.gz"

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
                dependsOn buildImage
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("${imageFile}")
            }
        """

        when:
        build('saveImage')

        then:
        noExceptionThrown()
        file(imageFile).size() > 0
    }

    def "Can docker image be saved with compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImageControl(type: DockerSaveImage) {
                dependsOn buildImage
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("${controlSavedImage}")
            }            

            task saveImage(type: DockerSaveImage) {
                dependsOn saveImageControl
                useCompression = true
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("${compressedImageFile}")
            }
        """
        when:
        build('saveImage')

        then:
        noExceptionThrown()
        file(controlSavedImage).size() > file(compressedImageFile).size()
    }

    File file(String relativePath) {
        File retVal = new File(projectDir, relativePath)
        assert retVal.exists()
        return retVal
    }
}
