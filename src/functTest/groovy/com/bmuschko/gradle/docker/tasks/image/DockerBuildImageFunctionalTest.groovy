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
