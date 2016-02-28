package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerVersionFunctionalTest extends AbstractFunctionalTest {
    def "Can get Docker version"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerVersion

            task dockerVersion(type: DockerVersion)
        """

        when:
        BuildResult result = build('dockerVersion')

        then:
        result.output.contains('Retrieving Docker version.')
    }
}
