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
package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerOnFailureFunctionalTest extends AbstractFunctionalTest {
    
    def "Catch exception on removal of non-existent container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeNonExistentContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { "abcdefgh1234567890" }
                onError { exception ->
                    if (exception.message.contains("No such container")) {
                       println "Caught Exception onFailure"
                    } 
                }
            }

            task workflow {
                dependsOn removeNonExistentContainer
            }
        """

        when:
        BuildResult result = build('workflow')

        then:
        result.output.contains('Caught Exception onFailure')
    }
    
    def "Re-throw exception on removal of non-existent container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeNonExistentContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { "abcdefgh1234567890" }
                onError { exception ->
                    if (exception.message.contains("No such container")) {
                       throw exception
                    } 
                }
            }

            task workflow {
                dependsOn removeNonExistentContainer
            }
        """

        expect:
        BuildResult result = buildAndFail('workflow')
    }
}

