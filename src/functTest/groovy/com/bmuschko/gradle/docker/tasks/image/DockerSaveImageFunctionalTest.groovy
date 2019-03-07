package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest

class DockerSaveImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE_ID = createUniqueImageId()
    private static final String BUILD_DIR = 'build/docker'
    private static final String CONTROL_SAVED_IMAGE = "$BUILD_DIR/$IMAGE_ID-docker-image-control.tar"
    private static final String IMAGE_FILE = "$BUILD_DIR/$IMAGE_ID-docker-image.tar"
    private static final String COMPRESSED_IMAGE_FILE = "$BUILD_DIR/$IMAGE_ID-compressed-docker-image.tar.gz"

    def setup() {
        buildFile << buildImage()
    }

    def "can save Docker image without compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn buildImage
                finalizedBy removeImage
                tag = "latest"
                repository = "${IMAGE_ID}"
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        build('saveImage')

        then:
        file(IMAGE_FILE).size() > 0
    }

    def "can save Docker image with compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImageControl(type: DockerSaveImage) {
                dependsOn buildImage
                tag = "latest"
                repository = "${IMAGE_ID}"
                destFile = file("${CONTROL_SAVED_IMAGE}")
            }

            task saveImage(type: DockerSaveImage) {
                dependsOn saveImageControl
                finalizedBy removeImage
                useCompression = true
                tag = "latest"
                repository = "${IMAGE_ID}"
                destFile = file("${COMPRESSED_IMAGE_FILE}")
            }
        """
        when:
        build('saveImage')

        then:
        file(CONTROL_SAVED_IMAGE).size() > file(COMPRESSED_IMAGE_FILE).size()
    }

    static String buildImage() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("${BUILD_DIR}")
                tags.add("${IMAGE_ID}")
                labels = ["setup":"${IMAGE_ID}"]
            }
            
            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId buildImage.getImageId()
            }
        """
    }
}
