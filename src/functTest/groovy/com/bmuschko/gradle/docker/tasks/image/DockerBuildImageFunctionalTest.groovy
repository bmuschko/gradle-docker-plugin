package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerBuildImageFunctionalTest extends AbstractFunctionalTest {

    def "can build image"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage')

        then:
        result.standardOutput.contains("Created image with ID")
    }

    def "building an image with the same ID marks task UP-TO-DATE"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage', '-i')

        then:
        result.task(':buildImage').outcome == SUCCESS
        result.standardOutput.contains('Created image with ID')
        result.standardOutput.contains('No previously saved imageId exists')

        when:
        result = build('buildImage', '-i')

        then:
        result.task(':buildImage').outcome == UP_TO_DATE
        !result.standardOutput.contains('Created image with ID')
        result.standardOutput.contains('found via call to inspectImage')
    }

    private String imageCreation() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerFile(type: Dockerfile) {
                from 'ubuntu:12.04'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerFile
                inputDir = file("build/docker")
            }
        """
    }
}
