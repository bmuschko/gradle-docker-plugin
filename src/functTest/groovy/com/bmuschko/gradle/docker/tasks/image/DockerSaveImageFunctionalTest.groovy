package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerSaveImageFunctionalTest extends AbstractFunctionalTest {

    final String ImageId = createUniqueImageId()
    final String controlSavedImage = "build/docker/${ImageId}-docker-image-control.tar"

    def setup() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tag = "${ImageId}"
                labels = ["setup":"${ImageId}"]
            }
            task saveImageControl(type: DockerSaveImage) {
                dependsOn buildImage
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("${controlSavedImage}")
            }            
        """

        when:
        BuildResult result = build('saveImageControl')

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
                destFile = file("build/docker/${ImageId}-docker-image.tar")
            }
        """

        when:
        build('saveImage')

        then:
        noExceptionThrown()
        fileSizesMatch(new File(projectDir, "build/docker/${ImageId}-docker-image.tar"), new File(projectDir, controlSavedImage))
    }

    def "Can docker image be saved with compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn buildImage
                useCompression = true
                tag = "${ImageId}"
                repository = "${ImageId}"
                destFile = file("build/docker/${ImageId}-docker-image.tar.gz")
            }
        """

        when:
        build('saveImage')

        then:
        noExceptionThrown()
        fileSizeLess(new File(projectDir, "build/docker/${ImageId}-docker-image.tar.gz"), new File(projectDir, controlSavedImage))
    }

    void fileSizesMatch(File file1, File file2) {
        long size1 = file1.size()
        long size2 = file2.size()
        if( size1 < size2 ) {
            assert (size2 % size1) <= 100
        }
        else {
            assert (size1 % size2) <= 100
        }
    }

    void fileSizeLess(File file1, File file2) {
        long size1 = file1.size()
        long size2 = file2.size()
        assert size1 < size2
    }
}
