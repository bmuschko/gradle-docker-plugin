package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest

class DockerSaveImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE = "alpine:3.4"
    private static final String BUILD_DIR = 'build/docker'
    private static final String CONTROL_SAVED_IMAGE = "$BUILD_DIR/alpine-3.4-docker-image-control.tar"
    private static final String IMAGE_FILE = "$BUILD_DIR/alpine-3.4-docker-image.tar"
    private static final String COMPRESSED_IMAGE_FILE = "$BUILD_DIR/alpine-3.4-compressed-docker-image.tar.gz"

    def "can save Docker image without compression"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                tag = "${IMAGE}"
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
                tag = "${IMAGE}"
                destFile = file("${CONTROL_SAVED_IMAGE}")
            }

            task saveImage(type: DockerSaveImage) {
                dependsOn saveImageControl
                useCompression = true
                tag = "${IMAGE}"
                destFile = file("${COMPRESSED_IMAGE_FILE}")
            }
        """
        when:
        build('saveImage')

        then:
        file(CONTROL_SAVED_IMAGE).size() > file(COMPRESSED_IMAGE_FILE).size()
    }
}
