package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerRemoveImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can remove image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'jane.doe@example.com'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn buildImage
                force = true
                targetImageId buildImage.getImageId()
            }

            task removeImageAndCheckRemoval(type: DockerListImages) {
                dependsOn removeImage
                showAll = true
                dangling = true
            }
        """

        when:
        BuildResult result = build('removeImageAndCheckRemoval')

        then:
        !result.output.contains("repository")
    }

    def "can remove multiple images"() {
        // Given a few Official Images that are small... :-)
        String firstImage = "alpine:3"
        String secondImage = "busybox:1.36"
        String thirdImage = "bash:5.2.21"

        // And a build script that uses them...
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task firstDockerfile(type: Dockerfile) {
                from '$firstImage'
                label(['maintainer': 'jane.doe@example.com'])
            }
            task buildFirstImage(type: DockerBuildImage) {
                dependsOn firstDockerfile
                inputDir = file("build/docker")
            }

            task secondDockerfile(type: Dockerfile) {
                from '$secondImage'
                label(['maintainer': 'jane.doe@example.com'])
            }
            task buildSecondImage(type: DockerBuildImage) {
                dependsOn secondDockerfile
                inputDir = file("build/docker")
            }

            task thirdDockerfile(type: Dockerfile) {
                from '$thirdImage'
                label(['maintainer': 'jane.doe@example.com'])
            }
            task buildThirdImage(type: DockerBuildImage) {
                dependsOn thirdDockerfile
                inputDir = file("build/docker")
            }

            task removeImages(type: DockerRemoveImage) {
                dependsOn buildFirstImage
                dependsOn buildSecondImage
                dependsOn buildThirdImage

                force = true

                images(
                    buildFirstImage.getImageId(),
                    buildSecondImage.getImageId(),
                    buildThirdImage.getImageId()
                )
            }

            task removeImageAndCheckRemoval(type: DockerListImages) {
                dependsOn removeImages
                showAll = true
                dangling = true
            }
        """

        when:
        BuildResult result = build('removeImageAndCheckRemoval')

        then:
        !result.output.contains("repository")
    }


    def "can remove image tagged in multiple repositories"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerTagImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'jane.doe@example.com'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task tagImage(type: DockerTagImage) {
                dependsOn buildImage
                repository = "repository"
                tag = "tag2"
                targetImageId buildImage.getImageId()
            }

            task tagImageSecondTime(type: DockerTagImage) {
                dependsOn tagImage
                repository = "repository"
                tag = "tag2"
                targetImageId buildImage.getImageId()
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn tagImageSecondTime
                force = true
                targetImageId buildImage.getImageId()
            }

            task removeImageAndCheckRemoval(type: DockerListImages) {
                dependsOn removeImage
                showAll = true
                dangling = true
            }
        """

        when:
        BuildResult result = build('removeImageAndCheckRemoval')

        then:
        !result.output.contains("repository")
    }
}
