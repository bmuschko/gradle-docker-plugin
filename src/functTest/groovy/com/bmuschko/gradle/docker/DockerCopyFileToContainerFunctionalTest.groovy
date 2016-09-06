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
class DockerCopyFileToContainerFunctionalTest extends AbstractFunctionalTest {

    def "Copy file into container"() {
            
        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') { 
            it.write('Hello, World!')
        }    
            
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd = ['echo', 'Hello World']
            }

            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/HelloWorld.txt"
                remotePath = "/root"
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn copyFileIntoContainer
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        build('workflow')
    }
    
    def "Copy tarfile into container"() {
            
        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') { 
            it.write('Hello, World!')
        }    
            
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createTarFile(type: Tar) {
                from "$projectDir/HelloWorld.txt"
                baseName = 'HelloWorld'
                destinationDir = projectDir
                extension = 'tgz'
                compression = Compression.GZIP
            }
			
            task pullImage(type: DockerPullImage) {
                dependsOn createTarFile
                repository = 'alpine'
                tag = 'latest'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd = ['echo', 'Hello World']
            }

            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                tarFile { new File("$projectDir/HelloWorld.tgz") }
                remotePath = "/root"
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn copyFileIntoContainer
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        build('workflow')
    }
    
    def "Fail if both hostPath and tarFile are specified"() {
            
        new File("$projectDir/HelloWorld.txt").withWriter('UTF-8') { 
            it.write('Hello, World!')
        }    
            
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileToContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = 'latest'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                cmd = ['echo', 'Hello World']
            }

            task copyFileIntoContainer(type: DockerCopyFileToContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                hostPath = "$projectDir/HelloWorld.txt"
                tarFile { new File("$projectDir/HelloWorld.txt") }
                remotePath = "/root"
            }

            task workflow {
                dependsOn copyFileIntoContainer
            }
        """

        when:
            build('workflow')

        then:
            Exception ex = thrown()
            ex.message.contains('Can specify either hostPath or tarFile not both')
    }
}
