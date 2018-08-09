package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerInfoFunctionalTest extends AbstractGroovyDslFunctionalTest {
    def "Can retrieve Docker info"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo)
        """

        when:
        BuildResult result = build('dockerInfo')

        then:
        result.output.contains('Retrieving Docker info.')
    }

    def "Passes dockerinfo to onNext if provided, w/o logger output"() {
        given:
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
        result.output.contains('Retrieving Docker info.')
        result.output.contains('Architecture found')
        !result.output.contains('Containers')
    }

    def "Can provide custom URL"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo) {
                url = "$dockerServerUrl"
            }
        """

        when:
        BuildResult result = build('dockerInfo')

        then:
        result.output.contains('Retrieving Docker info.')
    }

    def "Fails with illegal custom url"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerInfo

            task dockerInfo(type: DockerInfo) {
                url = "${dockerServerUrl}/url/to/fail"
            }
        """

        when:
        BuildResult result = buildAndFail('dockerInfo')

        then:
        result.task(':dockerInfo').outcome == TaskOutcome.FAILED
        result.output.contains('Not a directory')
    }
}
