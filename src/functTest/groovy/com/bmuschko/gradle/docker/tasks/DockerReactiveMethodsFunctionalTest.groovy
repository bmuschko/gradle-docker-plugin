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
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

import static com.bmuschko.gradle.docker.TestConfiguration.getDockerPrivateRegistryDomain

class DockerReactiveMethodsFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Catch exception on removal of non-existent container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeNonExistentContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId "abcdefgh1234567890"
                onError { exception ->
                    if (exception.message.contains("No such container")) {
                       println "Caught Exception onError"
                    } 
                }
            }
        """

        when:
        BuildResult result = build('removeNonExistentContainer')

        then:
        result.output.contains('Caught Exception onError')
    }

    def "Re-throw exception on removal of non-existent container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeNonExistentContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId "abcdefgh1234567890"
                onError { exception ->
                    if (exception.message.contains("No such container")) {
                       throw exception
                    } 
                }
            }
        """

        when:
        BuildResult result = buildAndFail('removeNonExistentContainer')

        then:
        result.output.contains('No such container')
    }

    def "should call onNext during build image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'john.doe@example.com'])
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy removeImage
                onNext {
                    if (it.stream) {
                        logger.quiet "docker: " + it.stream.trim()
                    }
                }
            }
            
            removeImage {
                targetImageId buildImage.imageId
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
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ["/bin/sh","-c","echo Hello World"]
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }
 
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                follow = true
                tailAll = true
                
                onNext { l ->
                    logger.quiet l.toString()
                }
            }
        """

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("STDOUT: Hello World")
    }

    def "should call onNext when listing images"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
    
            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task listImages(type: DockerListImages) {
                dependsOn pullImage
                onNext { images ->
                    images.each { image ->
                        logger.quiet "Tags : " + image.repoTags?.join(', ')
                        logger.quiet "Size : " + new java.text.DecimalFormat("#.##").format(image.size / (1024 * 1024)) + "MB"
                    }
                }
            }
        """

        when:
        BuildResult result = build('listImages')

        then:
        result.output.contains("MB")
    }

    def "should call onNext when copying file from container"() {
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId '$TEST_IMAGE_WITH_TAG'
                containerName = "$uniqueContainerName"
                cmd = ['/bin/sh']
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId createContainer.getContainerId()
            }

            task printOsRelease(type: DockerCopyFileFromContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                remotePath = "/etc/os-release"
                
                onNext { f ->
                    f.eachLine { l -> logger.quiet l }
                }
            }
        """

        when:
        BuildResult result = build('printOsRelease')

        then:
        result.output.contains('PRETTY_NAME="Alpine Linux v3.17"')
    }

    def "should call onNext when creating container"() {
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
            }

            task createContainer(type: DockerCreateContainer) {
                finalizedBy removeContainer
                targetImageId '$TEST_IMAGE_WITH_TAG'
                containerName = "$uniqueContainerName"
                cmd = ['/bin/sh']
                hostConfig.autoRemove = true
                
                onNext { c ->
                    if(c.warnings) {
                        throw new GradleException("Container created with warnings: " + c.warnings.join(','))
                    }
                }
            }
            
            removeContainer {
                targetContainerId createContainer.containerId
            }
            """

        expect:
        build('createContainer')
    }

    def "should call onNext when executing command in container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId '$TEST_IMAGE_WITH_TAG'
                cmd = ['sleep','10']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                commands.add(['echo', 'Hello World'] as String[])
                
                onNext { f ->
                    logger.quiet f.toString()
                }
            }
        """

        when:
        BuildResult result = build('execContainer')

        then:
        result.output.contains("STDOUT: Hello World")
    }

    def "should call onNext when inspecting container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId { '$TEST_IMAGE_WITH_TAG' }
                cmd = ['sleep','10']
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId createContainer.getContainerId()
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                finalizedBy removeContainer
                targetContainerId createContainer.getContainerId()
                
                onNext { c ->
                    if(!c.state.running) {
                        logger.error "Container should be running!"
                    }
                }
            }
        """

        when:
        def result = build('inspectContainer')

        then:
        result.output.contains("Container should be running!")
    }

    def "should call onNext when waiting for a container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId '$TEST_IMAGE_WITH_TAG'
                cmd = ['sh', '-c', 'exit 1']
                hostConfig.autoRemove = true
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            task stopContainer(type: DockerStartContainer) {
                targetContainerId createContainer.getContainerId()
            }

            task runContainers(type: DockerWaitContainer) {
                dependsOn startContainer
                finalizedBy stopContainer
                targetContainerId startContainer.getContainerId()
                onNext { r ->
                    if(r.statusCode) {
                        throw new GradleException("Container failed!")
                    }
                }
            }
        """

        when:
        BuildResult result = buildAndFail('runContainers')

        then:
        result.output.contains("Container failed!")
    }

    def "should call onNext when inspecting image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                
                onNext { image ->
                    logger.quiet 'Cmd:        ' + image.config.cmd
                    logger.quiet 'Entrypoint: ' + image.config.entrypoint
                }
            }
        """

        when:
        BuildResult result = build('inspectImage')

        then:
        result.output.contains('Entrypoint: null')
    }

    def "should call onNext when pulling image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId { 'busybox:musl' }
                onError {
                    // no op when image not found
                }
            }

            task pullImage(type: DockerPullImage) {
                dependsOn removeImage
                image = 'busybox:musl'
                
                onNext { p ->
                    logger.quiet p.status + ' ' + (p.progress ?: '')
                }
            }

        """

        when:
        BuildResult result = build('pullImage')

        then:
        result.output.contains('Pull complete')
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can build an image and push to private registry"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task createDockerfile(type: Dockerfile) {
                destFile = project.layout.buildDirectory.file('private-reg-reactive/Dockerfile')
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'benjamin.muschko@gmail.com'])
                runCommand 'mkdir -p /tmp/${createUniqueImageId()}'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn createDockerfile
                inputDir = createDockerfile.destFile.get().asFile.parentFile
                images.add('${dockerPrivateRegistryDomain}/${createUniqueImageId()}')
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId buildImage.imageId
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                finalizedBy removeImage
                images.set(buildImage.images)
                
                onNext { p ->
                    logger.quiet p.status + ' ' + (p.progress ?: '')
                }
            }
        """

        when:
        BuildResult result = build('pushImage')

        then:
        result.output.contains("Pushed")
    }

    def "should call onComplete when task finished without errors"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages

            task listImages(type: DockerListImages) {
                onComplete {
                    logger.quiet "-- END OF IMAGES --"
                }
            }
        """

        when:
        BuildResult result = build('listImages')

        then:
        result.output.contains("-- END OF IMAGES --")
    }

    def "should not call onComplete when task finished with error and onError is defined"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId { 'not_existing_image' }
                onError {
                    // no op
                }
                onComplete {
                    throw new GradleException("Should never go here!")
                }
            }
        """

        expect:
        build('removeImage')
    }

    def "should not call onComplete when task finished with error"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId 'not_existing_image' 
                onComplete {
                    throw new GradleException("Should never go here!")
                }
            }
        """

        when:
        BuildResult result = buildAndFail('removeImage')

        then:
        !result.output.contains("Should never go here!")
    }
}
