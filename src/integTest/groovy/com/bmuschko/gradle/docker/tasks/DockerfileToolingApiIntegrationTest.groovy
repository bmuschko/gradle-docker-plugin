package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.GradleInvocationResult
import com.bmuschko.gradle.docker.ToolingApiIntegrationTest

class DockerfileToolingApiIntegrationTest extends ToolingApiIntegrationTest {
    def "Supports incremental build"() {
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task dockerfile(type: Dockerfile) {
    from 'ubuntu:14.04'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
    runCommand 'echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
    runCommand 'apt-get update && apt-get clean'
    runCommand 'apt-get install -q -y openjdk-7-jre-headless && apt-get clean'
    addFile 'http://mirrors.jenkins-ci.org/war/1.563/jenkins.war', '/opt/jenkins.war'
    runCommand 'ln -sf /jenkins /root/.jenkins'
    entryPoint 'java', '-jar', '/opt/jenkins.war'
    exposePort 8080
    volume '/jenkins'
    defaultCommand ''
}
"""

        when:
        GradleInvocationResult result = runTasks('dockerfile')

        then:
        result.output.contains(':dockerfile')
        !result.output.contains('UP-TO-DATE')
        dockerfile.exists()

        when:
        result = runTasks('dockerfile')

        then:
        result.output.contains(':dockerfile UP-TO-DATE')
        dockerfile.exists()
    }
}
