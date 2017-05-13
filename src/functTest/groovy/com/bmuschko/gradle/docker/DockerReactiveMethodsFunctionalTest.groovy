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

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult

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
                       println "Caught Exception onError"
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
        result.output.contains('Caught Exception onError')
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
        buildAndFail('workflow')
    }

    def "should call onNext during build image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine:3.4'
                maintainer 'John Doe <john.doe@example.com>'
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

    def "should call onNext when coping file from container"() {
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId { 'alpine:3.4' }
                containerName = "$uniqueContainerName"
                cmd = ['/bin/sh']
            }

            task printOsRelease(type: DockerCopyFileFromContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                remotePath = "/etc/os-release"
                
                onNext { f ->
                    f.eachLine { l -> logger.quiet l }
                }
            }
        """

        when:
        def result = build('printOsRelease')

        then:
        result.output.contains('PRETTY_NAME="Alpine Linux v3.4"')
    }

    def "should call onNext when creating container"() {
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId { 'alpine:3.4' }
                containerName = "$uniqueContainerName"
                cmd = ['/bin/sh']
                
                onNext { c ->
                    if(c.warnings) {
                        throw new GradleException("Container created with warnings: " + c.warnings.join(','))
                    }
                }
            }
            """

        when:
        build('createContainer')

        then:
        noExceptionThrown()
    }

    def "should call onNext when executing command in container "() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId { 'alpine:3.4' }
                cmd = ['sleep','10']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                cmd = ['echo', 'Hello World']
                
                onNext { f ->
                    logger.quiet f.toString()
                }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn execContainer
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

    def "should call onNext when inspecting container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                targetImageId { 'alpine:3.4' }
                cmd = ['sleep','10']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                
                onNext { c ->
                    if(!c.state.running) {
                        logger.error "Container should be running!"
                    }
                }
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn inspectContainer
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }

            task workflow {
                dependsOn removeContainer
            }
        """

        expect:
        def result = build('workflow')
        result.output.contains("Container should be running!")
    }

    def "should call onNext when waiting for a container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer

            task createContainer(type: DockerCreateContainer){
                targetImageId { 'alpine:3.4' }
                cmd 'sh', '-c', 'exit 1'
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId {createContainer.getContainerId()}
            }

            task runContainers(type: DockerWaitContainer){
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
                onNext { r ->
                    if(r.statusCode) {
                        throw new GradleException("Container failed!")
                    }
                }
            }
        """

        expect:
        BuildResult result = buildAndFail('runContainers')
        result.output.contains("Container failed!")
    }

    def "should call onNext when inspecting image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = '3.4'
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn pullImage
                targetImageId { 'alpine:3.4' }
                
                onNext { image ->
                    logger.quiet 'Cmd:        ' + image.config.cmd
                    logger.quiet 'Entrypoint: ' + image.config.entrypoint
                }
            }

            task workflow {
                dependsOn inspectImage
            }
        """

        when:
        BuildResult result = build('workflow')

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
                repository = 'busybox'
                tag = 'musl'
                
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

    def "Can build an image and push to private registry"() {
        File dockerFileLocation = new File(getProjectDir(), 'build/private-reg-reactive/Dockerfile')
        if (!dockerFileLocation.parentFile.exists() && !dockerFileLocation.parentFile.mkdirs())
            throw new GradleException("Could not successfully create dockerFileLocation @ ${dockerFileLocation.path}")

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

            task createDockerfile(type: Dockerfile) {
                destFile = project.file("${dockerFileLocation.path}")
                from 'alpine:3.1'
                maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
                runCommand 'mkdir -p /tmp/${createUniqueImageId()}'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn createDockerfile
                inputDir = createDockerfile.destFile.parentFile
                tag = '${TestConfiguration.dockerPrivateRegistryDomain}/${createUniqueImageId()}'
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                conventionMapping.imageName = { buildImage.getTag() }
                
                onNext { p ->
                    logger.quiet p.status + ' ' + (p.progress ?: '')
                }
            }

            task workflow {
                dependsOn pushImage
            }
        """

        expect:
        BuildResult result = build('workflow')
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
        def result = build('listImages')

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

        when:
        build('removeImage')

        then:
        noExceptionThrown()
    }

    def "should not call onComplete when task finished with error"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId { 'not_existing_image' }                
                onComplete {
                    throw new GradleException("Should never go here!")
                }
            }
        """

        when:
        def result = buildAndFail('removeImage')

        then:
        !result.output.contains("Should never go here!")
    }
}
