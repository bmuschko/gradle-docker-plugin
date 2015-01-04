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
        assert dockerClient.dockerClientConfig.dockerCertPath == "${System.properties['user.home']}/.docker"
        assert dockerClient.dockerClientConfig.dockerCfgPath == "${System.properties['user.home']}/.dockercfg"
        assert dockerClient.dockerClientConfig.serverAddress == 'https://index.docker.io/v1/'
        assert dockerClient.dockerClientConfig.username == '${System.properties['user.name']}'
        assert !dockerClient.dockerClientConfig.password
        assert !dockerClient.dockerClientConfig.email
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
    url = 'http://remote.docker.com:2375'
    certPath = new File('${customCertPath.canonicalPath}')

    registry {
        url = 'https://some.registry.com/'
        username = 'johnny'
        password = 'pwd'
        email = 'john.doe@gmail.com'
    }
}

task customDocker(type: CustomDocker)

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask

class CustomDocker extends AbstractDockerRemoteApiTask {
    @Override
    void runRemoteCommand(dockerClient) {
        assert dockerClient
        assert dockerClient.dockerClientConfig.uri == new URI('http://remote.docker.com:2375')
        assert dockerClient.dockerClientConfig.dockerCertPath == '${customCertPath.canonicalPath}'
        assert dockerClient.dockerClientConfig.serverAddress == 'https://some.registry.com/'
        assert dockerClient.dockerClientConfig.username == 'johnny'
        assert dockerClient.dockerClientConfig.password == 'pwd'
        assert dockerClient.dockerClientConfig.email == 'john.doe@gmail.com'
    }
}
"""
        when:
        runTasks('customDocker')

        then:
        noExceptionThrown()
    }
}
