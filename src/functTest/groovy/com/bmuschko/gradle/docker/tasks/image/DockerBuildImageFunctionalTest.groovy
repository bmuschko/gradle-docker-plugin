package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerBuildImageFunctionalTest extends AbstractFunctionalTest {

    def "Can build image"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

task dockerFile(type: Dockerfile) {
    from 'ubuntu:12.04'
}

task buildImage(type: DockerBuildImage, dependsOn: dockerFile) {
    inputDir = file("build/docker")
}
"""
        when:
        BuildResult result = build('buildImage')

        then:
        !result.standardOutput.contains('Step 1 : FROM ubuntu:12.04')
        result.standardOutput.contains('Created image with ID')
    }

    def "Can build image and print stream"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

task dockerFile(type: Dockerfile) {
    from 'ubuntu:12.04'
}

task buildImage(type: DockerBuildImage, dependsOn: dockerFile) {
    inputDir = file("build/docker")
}
"""
        when:
        BuildResult result = build('buildImage', '-i')
        println result.standardOutput

        then:
        result.standardOutput.contains('Step 1 : FROM ubuntu:12.04')
        result.standardOutput.contains('Created image with ID')
    }

}
