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
class DockerWorkflowFunctionalTest extends AbstractFunctionalTest {
    def "Can create Dockerfile and build an image from it"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

task createDockerfile(type: Dockerfile) {
    destFile = project.file('build/mydockerfile/Dockerfile')
    from 'ubuntu:12.04'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}

task buildImage(type: DockerBuildImage) {
    dependsOn createDockerfile
    inputDir = createDockerfile.destFile.parentFile
    tag = "${createUniqueImageId()}"
}

task inspectImage(type: DockerInspectImage) {
    dependsOn buildImage
    targetImageId { buildImage.getImageId() }
}

task workflow {
    dependsOn inspectImage
}
"""
        when:
        BuildResult result = build('workflow')

        then:
        new File(projectDir, 'build/mydockerfile/Dockerfile').exists()
        result.standardOutput.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build and verify image"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

task buildImage(type: DockerBuildImage) {
    inputDir = file('images/minimal')
    tag = "${createUniqueImageId()}"
}

task inspectImage(type: DockerInspectImage) {
    dependsOn buildImage
    targetImageId { buildImage.getImageId() }
}

task workflow {
    dependsOn inspectImage
}
"""
        when:
        BuildResult result = build('workflow')

        then:
        result.standardOutput.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build an image, create and start a container"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
import com.bmuschko.gradle.docker.tasks.container.DockerKillContainer

task buildImage(type: DockerBuildImage) {
    inputDir = file('images/minimal')
    tag = "${createUniqueImageId()}"
}

task createContainer(type: DockerCreateContainer) {
    dependsOn buildImage
    targetImageId { buildImage.getImageId() }
    containerName = "$uniqueContainerName"
    portBindings = ['8080:8080']
}

task startContainer(type: DockerStartContainer) {
    dependsOn createContainer
    targetContainerId { createContainer.getContainerId() }
}

task inspectContainer(type: DockerInspectContainer) {
    dependsOn startContainer
    targetContainerId { startContainer.getContainerId() }
}

task killContainer(type: DockerKillContainer) {
    dependsOn inspectContainer
    targetContainerId { startContainer.getContainerId() }
}

task workflow {
    dependsOn killContainer
}
"""
        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("Name       : /$uniqueContainerName")
    }

    def "Can build an image, create and link a container"() {
        File imageDir = temporaryFolder.newFolder('images', 'minimal')
        createDockerfile(imageDir)

        String uniqueContainerName = createUniqueContainerName()

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

task buildImage(type: DockerBuildImage) {
    inputDir = file('images/minimal')
    tag = "${createUniqueImageId()}"
}

task createContainer1(type: DockerCreateContainer) {
    dependsOn buildImage
    targetImageId { buildImage.getImageId() }
    containerName = "${uniqueContainerName}1"
}

task createContainer2(type: DockerCreateContainer) {
    dependsOn createContainer1
    targetImageId { buildImage.getImageId() }
    containerName = "${uniqueContainerName}2"
    links = ["${uniqueContainerName}1:container1"]
}

task inspectContainer(type: DockerInspectContainer) {
    dependsOn createContainer2
    targetContainerId { createContainer2.getContainerId() }
}

task workflow {
    dependsOn inspectContainer
}
"""
        expect:
        BuildResult result = build('workflow')
        result.standardOutput.contains("Links      : [${uniqueContainerName}1:container1]")
    }

    @Requires({ TestPrecondition.DOCKERHUB_CREDENTIALS_AVAILABLE })
    def "Can push image to DockerHub and pull it afterward"() {
        buildFile << """
docker {
    registryCredentials {
        username = project.hasProperty('dockerHubUsername') ? project.property('dockerHubUsername') : null
        password = project.hasProperty('dockerHubPassword') ? project.property('dockerHubPassword') : null
        email = project.hasProperty('dockerHubEmail') ? project.property('dockerHubEmail') : null
    }
}

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

task createContainer(type: DockerCreateContainer) {
    imageId = 'busybox'
    cmd = ['true'] as String[]
}

task commitImage(type: DockerCommitImage) {
    dependsOn createContainer
    repository = "\$docker.registryCredentials.username/busybox"
    targetContainerId { createContainer.getContainerId() }
}

task pushImage(type: DockerPushImage) {
    dependsOn commitImage
    imageName = "\$docker.registryCredentials.username/busybox"
}

task pullImage(type: DockerPullImage) {
    dependsOn pushImage
    repository = "\$docker.registryCredentials.username/busybox"
}

task workflow {
    dependsOn pullImage
}
"""

        expect:
        build('workflow')
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can build an image and push to private registry"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

task createDockerfile(type: Dockerfile) {
    destFile = project.file('build/mydockerfile/Dockerfile')
    from 'ubuntu:12.04'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}

task buildImage(type: DockerBuildImage) {
    dependsOn createDockerfile
    inputDir = createDockerfile.destFile.parentFile
    tag = '${TestConfiguration.dockerPrivateRegistryDomain}/${createUniqueImageId()}'
}

task pushImage(type: DockerPushImage) {
    dependsOn buildImage
    conventionMapping.imageName = { buildImage.getTag() }
}

task workflow {
    dependsOn pushImage
}
"""
        when:
        build('workflow')

        then:
        new File(projectDir, 'build/mydockerfile/Dockerfile').exists()
        noExceptionThrown()
    }

    private File createDockerfile(File imageDir) {
        File dockerFile = new File(imageDir, 'Dockerfile')

        dockerFile << """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        dockerFile
    }
}
