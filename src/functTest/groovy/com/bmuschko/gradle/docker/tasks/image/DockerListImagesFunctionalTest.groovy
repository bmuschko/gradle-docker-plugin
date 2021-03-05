package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore

class DockerListImagesFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE_ID = createUniqueImageId()

    def setup() {
        buildFile << listImages()
    }

    def "can list images with default next handler"() {
        when:
        BuildResult result = build('listImages')

        then:
        result.output.contains("Repository Tags : ${IMAGE_ID}:latest")
    }

    def "can list images with default property values"() {
        given:
        buildFile << """
            listImages {
                onNext { images ->
                    if (!images) {
                        throw new GradleException("should find the image from setup")
                    }
                }
            }
        """

        expect:
        build('listImages')
    }

    def "can list images with labels filter"() {
        given:
        buildFile << """
            listImages {
                showAll = true
                labels = ["setup":"$IMAGE_ID"]

                onNext { images ->
                    if(!images.every { image -> image.repoTags.contains("${IMAGE_ID}:latest") }) {
                        throw new GradleException("should only find the image from setup")
                    }
                }
            }
        """

        expect:
        build('listImages')
    }

    @Ignore("Failing consistently - needs investigation")
    def "can list images with image name filter"() {
        given:
        buildFile << """
            listImages {
                showAll = true
                imageName = "${IMAGE_ID}"

                onNext { images ->
                    if(!images.every { image -> image.repoTags.contains("${IMAGE_ID}:latest") }) {
                        throw new GradleException("should only find the image from setup")
                    }
                }
            }
        """

        expect:
        build('listImages')
    }

    @Ignore("Failing consistently - needs investigation")
    def "can list images and handle empty result"() {
        given:
        buildFile << """
            listImages {
                showAll = true
                imageName = "${IMAGE_ID}:blah"

                onNext { images ->
                    if (images) {
                        throw new GradleException("should not find any image")
                    }
                }
            }
        """

        expect:
        build('listImages')
    }

    private static String listImages() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                images.add("${IMAGE_ID}")
                labels = ["setup":"${IMAGE_ID}"]
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId buildImage.imageId
            }
            
            task listImages(type: DockerListImages) {
                dependsOn buildImage
                finalizedBy removeImage
            }
        """
    }
}
