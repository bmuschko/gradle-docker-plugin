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
class DockerReactiveMethodsFunctionalTest extends AbstractFunctionalTest {

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

    def "should call onNext during build image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine:3.4'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                onNext {
                    logger.quiet "docker: " + it.stream.trim()
                }
            }
        """

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains("docker: Step 1")
    }

    def "should call onNext when fetching container logs"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.repository+":"+pullImage.tag }
                cmd = ["/bin/sh","-c","echo Hello World"]
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }
 
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                follow = true
                tailAll = true
                
                onNext { l ->
                    logger.quiet l.toString()
                }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn logContainer
                removeVolumes = true
                force = true
                targetContainerId { startContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
           
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("STDOUT: Hello World")
    }

    def "should call onNext when listing images"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
    
            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task listImages(type: DockerListImages) {
                dependsOn pullImage
                onNext { images ->
                    images.find { it.size < 5 * 1024 * 1024 }
                          .each { image ->
                              logger.quiet "Tags : " + image.repoTags?.join(', ')
                              logger.quiet "Size : " + new java.text.DecimalFormat("#.##").format(image.size / (1024 * 1024)) + "MB"
                          }
                }
            }
        """

        when:
        def result = build('listImages')

        then:
        result.output.contains("MB")
    }
}

