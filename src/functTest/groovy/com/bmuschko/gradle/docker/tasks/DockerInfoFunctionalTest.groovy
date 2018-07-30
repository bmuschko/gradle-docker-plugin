package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult

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

    def "Passes dockerinfo to onNext if provided, w/o logger output"() {
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
        && result.output.contains('Architecture found')
        && !result.output.contains('Containers'))
    }

}
