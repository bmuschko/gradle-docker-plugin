package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import groovy.json.JsonSlurper
import org.apache.commons.vfs2.VFS
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED

class DockerSaveImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE_BASE = 'alpine'
    private static final String IMAGE_3_2 = "$IMAGE_BASE:3.2"
    private static final String IMAGE_3_3 = "$IMAGE_BASE:3.3"
    private static final String IMAGE_3_4 = "$IMAGE_BASE:3.4"
    private static final String BUILD_DIR = 'build/docker'
    private static final String CONTROL_SAVED_IMAGE = "$BUILD_DIR/alpine-docker-image-control.tar"
    private static final String IMAGE_FILE = "$BUILD_DIR/alpine-docker-image.tar"
    private static final String COMPRESSED_IMAGE_FILE = "$BUILD_DIR/alpine-compressed-docker-image.tar.gz"

    def "can save Docker image without compression"() {
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        build('saveImage')

        then:
        file(IMAGE_FILE).size() > 0
    }

    def "can save Docker image with compression"() {
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImageControl(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${CONTROL_SAVED_IMAGE}")
            }

            task saveImage(type: DockerSaveImage) {
                dependsOn saveImageControl
                useCompression = true
                images.add("${IMAGE_3_4}")
                destFile = file("${COMPRESSED_IMAGE_FILE}")
            }
        """
        when:
        build('saveImage')

        then:
        file(CONTROL_SAVED_IMAGE).size() > file(COMPRESSED_IMAGE_FILE).size()
    }

    @Unroll
    def "only saves specific Docker image [image: #image]"() {
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << pullImageTask('pullImage2', IMAGE_3_3)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn tasks.withType(DockerPullImage)
                images.add("${image}")
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        build('saveImage')

        then:
        getRepoTags(IMAGE_FILE) == [image]

        where:
        image << [IMAGE_3_3, IMAGE_3_4]
    }

    def "saves all Docker images of specified base"() {
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << pullImageTask('pullImage2', IMAGE_3_3)
        buildFile << pullImageTask('pullImage3', IMAGE_3_2)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn tasks.withType(DockerPullImage)
                images.add("${IMAGE_BASE}")
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        build('saveImage')

        then:
        def repoTags = getRepoTags(IMAGE_FILE)
        repoTags.containsAll(IMAGE_3_2, IMAGE_3_3, IMAGE_3_4)
        repoTags.every { it.startsWith("${IMAGE_BASE}:") }
    }

    def "only saves specific Docker images"() {
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << pullImageTask('pullImage2', IMAGE_3_3)
        buildFile << pullImageTask('pullImage3', IMAGE_3_2)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn tasks.withType(DockerPullImage)
                images.addAll("${IMAGE_3_3}", "${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        build('saveImage')

        then:
        getRepoTags(IMAGE_FILE) == [IMAGE_3_3, IMAGE_3_4]
    }

    def "skips execution if images is set to null"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                images.set(null as Set)
                destFile = file("${IMAGE_FILE}")
            }
        """

        when:
        def result = build('saveImage')

        then:
        result.task(":saveImage").outcome == SKIPPED
        !new File(projectDir, IMAGE_FILE).exists()
    }

    def getRepoTags(imageFile) {
        VFS.manager.resolveFile("tar:${file(imageFile).toURI()}!/manifest.json").withCloseable { manifest ->
            manifest.content.withCloseable { content ->
                content.inputStream.withStream { contentStream ->
                    new JsonSlurper().parse(contentStream)
                }
            }
        }.RepoTags.flatten().sort()
    }

    static String pullImageTask(taskName, image) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task ${taskName}(type: DockerPullImage) {
                image = "${image}"
            }
        """
    }
}
