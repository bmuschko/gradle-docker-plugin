/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerOperationFunctionalTest extends AbstractGroovyDslFunctionalTest {
    def "Can get DockerClient with onNext defined"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerOperation

            task dockerClient(type: DockerOperation) {
                onNext { client ->
                    if (client != null) {
                        logger.quiet "Found Version: " + client.versionCmd().exec().version
                    } else {
                        logger.quiet 'Client is NULL'
                    }
                }
            }
        """

        when:
        BuildResult result = build('dockerClient')

        then:
        result.output.contains('Found Version: ')
        !result.output.contains('Client is NULL')
    }

    def "Print informational message when onNext is NOT defined"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.DockerOperation

            task dockerClient(type: DockerOperation)
        """

        when:
        BuildResult result = build('dockerClient')

        then:
        result.output.contains('Execution amounts to a no-op')
    }
}
