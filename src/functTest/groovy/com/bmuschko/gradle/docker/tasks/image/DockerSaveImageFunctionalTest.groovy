package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import groovy.json.JsonSlurper
import org.apache.commons.vfs2.VFS
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class DockerSaveImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE_BASE = 'alpine'
    private static final String IMAGE_3_2 = "$IMAGE_BASE:3.2"
    private static final String IMAGE_3_3 = "$IMAGE_BASE:3.3"
    private static final String IMAGE_3_4 = "$IMAGE_BASE:3.4"
    private static final String BUILD_DIR = 'build/docker'
    private static final String CONTROL_SAVED_IMAGE = "$BUILD_DIR/alpine-docker-image-control.tar"
    private static final String IMAGE_FILE = "$BUILD_DIR/alpine-docker-image.tar"
    private static final String COMPRESSED_IMAGE_FILE = "$BUILD_DIR/alpine-compressed-docker-image.tar.gz"
    private static final String IMAGE_IDS_FILE = "$BUILD_DIR/imageIds.properties"

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
        repoTags.every {
            it.startsWith("${IMAGE_BASE}:")
        }
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

    def "task not up-to-date when imageIds file is missing"() {
        given:
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
                imageIdsFile = file("${IMAGE_IDS_FILE}")
            }
        """

        when:
        def result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS

        when:
        result = build('saveImage')

        then:
        result.task(':saveImage').outcome == UP_TO_DATE

        when:
        file(IMAGE_IDS_FILE).delete()
        result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS
    }

    def "task not up-to-date when images in file do not match configuration"() {
        given:
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
                doLast {
                    imageIdsFile.get().asFile.text = "a = b"
                }
            }
        """

        when:
        def result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS

        when:
        result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS
    }

    def "task not up-to-date when saved image is missing on daemon"() {
        given:
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
            }

            task deleteImage(type: DockerRemoveImage) {
                dependsOn saveImage
                force = true
                imageId = saveImage.images.get().first()
            }
        """

        when:
        def result = build('deleteImage')

        then:
        result.task(':saveImage').outcome == SUCCESS

        when:
        result = buildAndFail('saveImage', '-x', 'pullImage')

        then:
        result.task(':saveImage').outcome == FAILED
    }

    def "task not up-to-date when image has a different id"() {
        given:
        buildFile << pullImageTask('pullImage', IMAGE_3_4)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage

            task saveImage(type: DockerSaveImage) {
                dependsOn pullImage
                images.add("${IMAGE_3_4}")
                destFile = file("${IMAGE_FILE}")
                imageIdsFile = file("${IMAGE_IDS_FILE}")
            }
        """

        when:
        def result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS

        when:
        def imageIds = new Properties()
        file(IMAGE_IDS_FILE).withInputStream {
            imageIds.load(it)
        }
        imageIds.setProperty(imageIds.stringPropertyNames().first(), 'foo')
        file(IMAGE_IDS_FILE).withOutputStream {
            imageIds.store(it, null)
        }
        result = build('saveImage')

        then:
        result.task(':saveImage').outcome == SUCCESS
    }

    def getRepoTags(imageFile) {
        VFS.manager.resolveFile("tar:${file(imageFile).toURI()}!/manifest.json").withCloseable { manifest ->
            manifest.content.withCloseable { content ->
                content.inputStream.withStream { contentStream ->
                    new JsonSlurper().parse(contentStream)
                }
            }
        }.RepoTags.flatten().findAll {it != null}.sort()
    }

    static String pullImageTask(taskName, image) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task ${taskName}(type: DockerPullImage) {
                image = "${image}"
            }
        """
    }

    def "can use configuration cache"() {
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
        BuildResult result = build('saveImage')

        then:
        file(IMAGE_FILE).size() > 0
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build('saveImage')

        then:
        result.output.contains("Configuration cache entry reused.")
    }
}
