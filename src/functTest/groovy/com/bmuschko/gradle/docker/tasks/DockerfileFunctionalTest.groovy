package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerfileFunctionalTest extends AbstractFunctionalTest {
    static final String DOCKERFILE_TASK_NAME = 'dockerfile'

    def "Same instructions does not mark task UP-TO-DATE"() {
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
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
        BuildResult result = build(DOCKERFILE_TASK_NAME)

        then:
        !isDockerfileTaskUpToDate(result)
        dockerfile.exists()

        when:
        result = build(DOCKERFILE_TASK_NAME)

        then:
        !isDockerfileTaskUpToDate(result)
        dockerfile.exists()
    }

    def "Adding more instructions does not mark task UP-TO-DATE"() {
        File dockerfileDir = temporaryFolder.newFolder('build', 'docker')
        File dockerfile = new File(dockerfileDir, 'Dockerfile')

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    from 'ubuntu:14.04'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}
"""

        when:
        BuildResult result = build(DOCKERFILE_TASK_NAME)

        then:
        !isDockerfileTaskUpToDate(result)
        dockerfile.exists()

        when:
        new File(dockerfileDir, 'test.txt').createNewFile()
        buildFile << """
${DOCKERFILE_TASK_NAME}.addFile('test.txt', '/app/')
"""
        result = build(DOCKERFILE_TASK_NAME)

        then:
        !isDockerfileTaskUpToDate(result)
        dockerfile.exists()
    }

    private boolean isDockerfileTaskUpToDate(BuildResult result) {
        result.standardOutput.contains(":$DOCKERFILE_TASK_NAME UP-TO-DATE")
    }
}
