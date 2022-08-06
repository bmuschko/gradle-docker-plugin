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
import spock.lang.Requires

import static com.bmuschko.gradle.docker.TextUtils.escapeFilePath

class DockerWorkflowFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Can build an image, create and link a container"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                images.add("${createUniqueImageId()}")
            }

            task createContainer1(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}1"
                cmd = ['/bin/sh']
            }

            task createContainer2(type: DockerCreateContainer) {
                dependsOn createContainer1
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}2"
                hostConfig.links = ["${uniqueContainerName}1:container1"]
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer2
                targetContainerId createContainer2.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Links       : [${uniqueContainerName}1:container1]")
    }

    def "Can build an image, create a container and link its volumes into another container"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        dockerFile << 'VOLUME /data'

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                images.add("${createUniqueImageId()}")
            }

            task createContainer1(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}-1"
                cmd = ['/bin/sh']
            }

            task createContainer2(type: DockerCreateContainer) {
                dependsOn createContainer1
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}-2"
                hostConfig.volumesFrom = ["${uniqueContainerName}-1"]
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer2
                targetContainerId createContainer2.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("VolumesFrom : [${uniqueContainerName}-1:rw]")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can build an image and push to private registry"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

            task createDockerfile(type: Dockerfile) {
                destFile = project.layout.buildDirectory.file('private-reg/Dockerfile')
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'benjamin.muschko@gmail.com'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn createDockerfile
                inputDir = createDockerfile.destFile.get().asFile.parentFile
                images.add('${TestConfiguration.dockerPrivateRegistryDomain}/${createUniqueImageId()}')
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                images.set(buildImage.images)
            }

            task workflow {
                dependsOn pushImage
            }
        """

        when:
        build('workflow')

        then:
        new File(projectDir, 'build/private-reg/Dockerfile').isFile()
    }

    def "Can build an image, create a container, and copy file from it"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        // required for task `copyFileFromContainerToHostDir`
        File hostPathDir = new File(getProjectDir(), "copy-file-host-dir")
        if (!hostPathDir.mkdirs())
            throw new GradleException("Could not successfully create hostPathDir @ ${hostPathDir.path}")

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file('images/minimal')
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "$uniqueContainerName"
                cmd = ['/bin/sh']
            }

            task copyFileFromContainerToHostFile(type: DockerCopyFileFromContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, '/copy-file-host-file/shebang.tar'))}"
                remotePath = "/bin/sh"
                compressed = true
            }

            task copyFileFromContainerToHostDir(type: DockerCopyFileFromContainer) {
                dependsOn copyFileFromContainerToHostFile
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, 'copy-file-host-dir'))}"
                remotePath = "/bin/sh"
            }

            task copyDirFromContainerToHostDir(type: DockerCopyFileFromContainer) {
                dependsOn copyFileFromContainerToHostDir
                targetContainerId createContainer.getContainerId()
                hostPath = "${escapeFilePath(new File(projectDir, 'copy-dir'))}"
                remotePath = "/var/spool"
            }

            task workflow {
                dependsOn copyDirFromContainerToHostDir
            }
        """

        when:
        build('workflow')

        then:
        new File("$projectDir/copy-file-host-file/shebang.tar").exists()
        new File("$projectDir/copy-file-host-dir/sh").exists()
        new File("$projectDir/copy-dir").exists()
    }

    def "Can build an image, create a container and expose a port"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                exposePorts("tcp", [9999])
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("ExposedPorts : [9999/tcp]")
    }

    def "Can build an image, create a container and set LogConfig"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                hostConfig.logConfig("none", [:])
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("LogConfig : none")
    }

    def "Can build an image, create a container and set RestartPolicy"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                hostConfig.restartPolicy("on-failure", 999)
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("RestartPolicy : on-failure:999")
    }

    def "Can build an image, create a container and set devices"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                hostConfig.devices = ["/dev/sda:/dev/xvda:rwm"]
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains("Devices : [/dev/sda:/dev/xvda:rwm]")
    }

    def "Can build an image, create a container and set /dev/shm size"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                hostConfig.portBindings = ['8080:8080']
                hostConfig.shmSize = 128000L
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
                onNext { c ->
                    if(c.hostConfig.shmSize != 128000) {
                        throw new GradleException("Invalid ShmSize value!")
                    }
                    if(!c.hostConfig.portBindings) {
                        throw new GradleException("Invalid port bindings!")
                    }
                }
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        build('workflow')
    }

    def "Can build an image, create a container and assign an entrypoint"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                entrypoint = ['env']
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
                onNext { container ->
                    println container.config.entrypoint
                    assert container.config.entrypoint == ['env']
                }
            }

            task workflow {
                dependsOn inspectContainer
            }

        """

        expect:
        BuildResult result = build('workflow')
        result.output.contains('[env]')
    }

    def "Can build an image and create a container with labels"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${createUniqueImageId()}")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
                containerName = "${uniqueContainerName}"
                labels = ["test.label1":"aaa", "test.label2": "bbb"]
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
                onNext { c ->
                    if(c.config.labels.size() != 3) {
                        throw new GradleException("Invalid labels size!")
                    }
                }
            }

            task workflow {
                dependsOn inspectContainer
            }
        """

        expect:
        build('workflow')
    }

    def "Can build an image and save it to a file and load"() {
        File imageDir = new File(temporaryFolder, 'images/minimal')
        imageDir.mkdirs()
        File dockerFile = createDockerfile(imageDir)
        dockerFile << "EXPOSE 8888" // add random instruction to be able to remove image
        String imageName = createUniqueImageId()
        String savedImagePath = "${escapeFilePath(new File(temporaryFolder, 'someFile.tmp'))}"
        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerSaveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerLoadImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            
            task buildImage(type: DockerBuildImage) {
                inputDir = file("${escapeFilePath(dockerFile.parentFile)}")
                images.add("${imageName}")
            }

            task saveImage(type: DockerSaveImage) {
                dependsOn buildImage
                images.add("${imageName}")
                destFile = new File("${savedImagePath}")
            }
            
            task removeImage(type: DockerRemoveImage) {
                dependsOn saveImage
                force = true
                targetImageId buildImage.getImageId()
            }
            
            task loadImage(type: DockerLoadImage) {
                dependsOn removeImage
                imageFile = saveImage.destFile
            }
            
            task createContainer(type: DockerCreateContainer) {
                dependsOn loadImage
                targetImageId "${imageName}"
                containerName = "${uniqueContainerName}"
            }

            task workflow {
                dependsOn createContainer
            }
        """

        expect:
        build('workflow')
        new File(savedImagePath).newInputStream().available() > 0
    }

    private File createDockerfile(File imageDir) {
        File dockerFile = new File(imageDir, 'Dockerfile')
        dockerFile << """
FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
"""
        dockerFile
    }
}
