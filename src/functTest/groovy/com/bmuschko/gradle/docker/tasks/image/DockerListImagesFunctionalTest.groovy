package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerListImagesFunctionalTest extends AbstractFunctionalTest {
    def "Can list images with default property values"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerListImages

task listImages(type: DockerListImages)
"""

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }

    def "Can list images with re-configured default property values"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerListImages

task listImages(type: DockerListImages) {
    showAll = true
    filters = '{"dangling":["true"]}'
}
"""

        when:
        build('listImages')

        then:
        noExceptionThrown()
    }
}
