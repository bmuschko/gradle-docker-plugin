package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerListImagesFunctionalTest extends AbstractFunctionalTest {

    final String ImageId = createUniqueImageId()

    def setup() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine'
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

    def "Can list images with default property values"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                onNext { images ->
                    if (!images) {
                        throw new GradleException("should find the image from setup")
                    }
                }
            }
        """

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }

    def "Can list images with labels filter"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                labels = ["setup":"${ImageId}"]

                onNext { images ->
                    if(!images.every { image -> image.repoTags.contains("${ImageId}:latest") }) {
                        throw new GradleException("should only find the image from setup")
                    }
                }
            }
        """

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }

    def "Can list images with image name filter"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                imageName = "${ImageId}"

                onNext { images ->
                    if(!images.every { image -> image.repoTags.contains("${ImageId}:latest") }) {
                        throw new GradleException("should only find the image from setup")
                    }
                }
            }
        """

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }

    def "can list images and handle empty result"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                imageName = "${ImageId}:blah"

                onNext { images ->
                    if (images) {
                        throw new GradleException("should not find any image")
                    }
                }
            }
        """

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }
}
