package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerMultipleClientFunctionalTest extends AbstractGroovyDslFunctionalTest {
    def "DockerClient respects task configuration"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerClient

            task dockerClient1(type: DockerClient) {
                onNext { client ->
                    if (client != null) {
                        logger.quiet "config: " + client.dockerClientConfig.toString()
                    } else {
                        logger.quiet 'Client is NULL'
                    }
                }
            }
            task dockerClient2(type: DockerClient) {
                url = 'tcp://docker.corp.com'
                onNext { client ->
                    if (client != null) {
                        logger.quiet "config: " + client.dockerClientConfig.toString()
                    } else {
                        logger.quiet 'Client is NULL'
                    }
                }
            }
            task dockerClient3(type: DockerClient) {
                url = 'tcp://docker.school.edu'
                certPath = new File('/tmp')
                onNext { client ->
                    if (client != null) {
                        logger.quiet "config: " + client.dockerClientConfig.toString()
                    } else {
                        logger.quiet 'Client is NULL'
                    }
                }
            }
        """

        when:
        BuildResult result = build('dockerClient1', 'dockerClient2', 'dockerClient3')

        then:
        result.output.contains('config: DefaultDockerClientConfig[dockerHost=unix:///var/run/docker.sock')
        result.output.contains('config: DefaultDockerClientConfig[dockerHost=tcp://docker.corp.com')
        result.output.contains('config: DefaultDockerClientConfig[dockerHost=tcp://docker.school.edu')
        !result.output.contains('Client is NULL')
    }

}
