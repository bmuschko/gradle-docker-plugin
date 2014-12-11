package com.bmuschko.gradle.docker

class AbstractDockerRemoteApiTaskIntegrationTest extends ToolingApiIntegrationTest {
    def "Can create and execute custom remote API task with default extension values"() {
        buildFile << """
task customDocker(type: CustomDocker)

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask

class CustomDocker extends AbstractDockerRemoteApiTask {
    @Override
    void runRemoteCommand(dockerClient) {
        assert dockerClient
        assert dockerClient.dockerClientConfig.uri == new URI('http://localhost:2375')
        assert !dockerClient.dockerClientConfig.username
        assert !dockerClient.dockerClientConfig.password
        assert !dockerClient.dockerClientConfig.email
        assert dockerClient.dockerClientConfig.dockerCertPath == "${System.properties['user.home']}/.docker"
    }
}
"""
        when:
        runTasks('customDocker')

        then:
        noExceptionThrown()
    }

    def "Can create and execute custom remote API task with extension values"() {
        File customCertPath = new File(projectDir, 'mydocker')
        createDir(customCertPath)
        buildFile << """
docker {
    serverUrl = 'http://remote.docker.com:2375'
    certPath = new File('${customCertPath.canonicalPath}')

    credentials {
        username = 'bmuschko'
        password = 'pwd'
        email = 'benjamin.muschko@gmail.com'
    }
}

task customDocker(type: CustomDocker)

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask

class CustomDocker extends AbstractDockerRemoteApiTask {
    @Override
    void runRemoteCommand(dockerClient) {
        assert dockerClient
        assert dockerClient.dockerClientConfig.uri == new URI('http://remote.docker.com:2375')
        assert dockerClient.dockerClientConfig.username == 'bmuschko'
        assert dockerClient.dockerClientConfig.password == 'pwd'
        assert dockerClient.dockerClientConfig.email == 'benjamin.muschko@gmail.com'
        assert dockerClient.dockerClientConfig.dockerCertPath == '${customCertPath.canonicalPath}'
    }
}
"""
        when:
        runTasks('customDocker')

        then:
        noExceptionThrown()
    }
}
