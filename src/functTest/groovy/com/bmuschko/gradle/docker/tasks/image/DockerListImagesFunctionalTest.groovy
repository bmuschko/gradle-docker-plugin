package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import spock.lang.Requires
import org.gradle.testkit.runner.BuildResult

class DockerListImagesFunctionalTest extends AbstractFunctionalTest {

    static final String ImageName = "docker-list-images-functional-test"

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
                tag = "${ImageName}"
                labels = ["setup":"${ImageName}"]
            }
        """

        when:
        build('buildImage')
    }

    def "Can list images with default property values"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages)
        """

        when:
        BuildResult result = build('listImages')

        then:
        noExceptionThrown()
        result.output.contains(ImageName)
    }

    def "Can list images with labels filter"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                labels = ["setup":"${ImageName}"]
            }
        """

        when:
        BuildResult result = build('listImages')

        then:
        noExceptionThrown()
        result.output.contains(ImageName)
    }

    def "Can list images with image name filter"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                imageName = "${ImageName}"
            }
        """

        when:
        BuildResult result = build('listImages')

        then:
        noExceptionThrown()
        result.output.contains(ImageName)
    }

    def "can list images and handle empty result"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                showAll = true
                imageName = "blah-blah"

                onNext {
                }
            }
        """

        when:
        BuildResult result = build('listImages')

        then:
        noExceptionThrown()
    }
}
