package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerListImagesFunctionalTest extends AbstractFunctionalTest {
    def "can list images"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerListImages

task listImages(type: DockerListImages)
"""

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }
}
