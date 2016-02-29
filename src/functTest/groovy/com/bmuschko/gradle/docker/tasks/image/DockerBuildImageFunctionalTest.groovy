package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerBuildImageFunctionalTest extends AbstractFunctionalTest {

    def "can build image"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains("Created image with ID")
    }

    def "building an image with the same ID marks task UP-TO-DATE"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage', '-i')

        then:
        result.task(':buildImage').outcome == SUCCESS
        result.output.contains('Created image with ID')
        result.output.contains('No previously saved imageId exists')

        when:
        result = build('buildImage', '-i')

        then:
        result.task(':buildImage').outcome == SKIPPED
        !result.output.contains('Created image with ID')
        result.output.contains('found via call to inspectImage')
    }

    def "building an image with the same ID by two different tasks mark second task UP-TO-DATE"() {
        buildFile << imageCreation()
        buildFile << """
            task buildImageAnother(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }
        """

        when:
        BuildResult result = build('buildImage', '-i')

        then:
        result.task(':buildImage').outcome == SUCCESS
        result.output.contains('Created image with ID')
        result.output.contains('No previously saved imageId exists')

        when:
        result = build('buildImageAnother', '-i')

        then:
        result.task(':buildImageAnother').outcome == SKIPPED
        !result.output.contains('Created image with ID')
        result.output.contains('found via call to inspectImage')
    }

    private String imageCreation() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'ubuntu:12.04'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }
        """
    }
}
