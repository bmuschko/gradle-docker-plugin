package com.bmuschko.gradle.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore

class DockerNettyDockerCmdExecFactoryFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String NETTY_DRIVER_IN_USE = "Using " + NettyDockerCmdExecFactory.class.simpleName + " as driver for " + DockerClient.class.simpleName

    def "By default NettyDockerCmdExecFactory is not in use"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('--debug', 'dockerInfo')

        then:
        !result.output.contains(NETTY_DRIVER_IN_USE)
    }

    def "Can use NettyDockerCmdExecFactory via Project Property"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('--debug', '-PgdpNettyExecFactory=true', 'dockerInfo')

        then:
        result.output.contains(NETTY_DRIVER_IN_USE)
    }

    def "Can use NettyDockerCmdExecFactory via System Property"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('--debug', '-Dgdp.netty.exec.factory=true', 'dockerInfo')

        then:
        result.output.contains(NETTY_DRIVER_IN_USE)
    }

    @Ignore
    def "Can use NettyDockerCmdExecFactory via Environment Variable"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('--debug', 'dockerInfo')

        then:
        result.output.contains(NETTY_DRIVER_IN_USE)
    }
}
