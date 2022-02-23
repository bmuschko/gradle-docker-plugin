package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerMultipleClientFunctionalTest extends AbstractGroovyDslFunctionalTest {
    def "DockerOperation respects task configuration"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerOperation

            task dockerClient1(type: DockerOperation) {
                onNext { client ->
                    if (client != null) {
                        logger.quiet "dockerHost: " + client.dockerClientConfig.dockerHost
                    }
                }
            }
            task dockerClient2(type: DockerOperation) {
                url = 'tcp://docker.corp.com:2375'
                onNext { client ->
                    if (client != null) {
                        logger.quiet "dockerHost: " + client.dockerClientConfig.dockerHost
                    }
                }
            }
            task dockerClient3(type: DockerOperation) {
                url = 'tcp://docker.school.edu:2375'
                certPath = new File('/tmp')
                onNext { client ->
                    if (client != null) {
                        logger.quiet "dockerHost: " + client.dockerClientConfig.dockerHost
                    }
                }
            }
        """

        when:
        BuildResult result = build('dockerClient1', 'dockerClient2', 'dockerClient3')

        then:
        result.output.contains('dockerHost: unix:///var/run/docker.sock')
        result.output.contains('dockerHost: tcp://docker.corp.com')
        result.output.contains('dockerHost: tcp://docker.school.edu')
    }

}
