package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

class DockerInfoFunctionalTest extends AbstractFunctionalTest {
    def "Can get Docker info"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('dockerInfo')

        then:
        result.output.contains('Retrieving Docker info.')
    }

    def "Calls onNext when provided"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo) {
                onNext { logger.quiet "In onNext as expected" }
            }
        """

        when:
        BuildResult result = build('dockerInfo')

        then:
        (result.output.contains('Retrieving Docker info.')
        && result.output.contains('In onNext as expected')
        && !result.output.contains('Containers'))
    }

    def "Passes dockerinfo to onNext"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo) {
                onNext { m ->
                    if (m.architecture) {  // Because I implemented and I care
                        logger.quiet "Architecture found"
                    }
                }
            }
        """

        when:
        BuildResult result = build('dockerInfo')

        then:
        (result.output.contains('Retrieving Docker info.')
        && result.output.contains('Architecture found'))
    }

}
