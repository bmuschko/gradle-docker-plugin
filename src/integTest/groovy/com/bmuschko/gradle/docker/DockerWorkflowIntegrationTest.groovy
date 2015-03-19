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

import org.apache.commons.io.FileUtils
import spock.lang.IgnoreIf

@IgnoreIf({ !AbstractIntegrationTest.isDockerServerInfoUrlReachable() })
class DockerWorkflowIntegrationTest extends ToolingApiIntegrationTest {
    def "Can get Docker version and info"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.DockerVersion
import com.bmuschko.gradle.docker.tasks.DockerInfo

task dockerVersion(type: DockerVersion)
task dockerInfo(type: DockerInfo)
"""
        when:
        GradleInvocationResult result = runTasks('dockerVersion', 'dockerInfo')

        then:
        result.output.contains('Retrieving Docker version.')
        result.output.contains('Retrieving Docker info.')
    }

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
"""
        when:
        GradleInvocationResult result = runTasks('inspectImage')

        then:
        new File(projectDir, 'build/mydockerfile/Dockerfile').exists()
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build and verify image"() {
        File imageDir = createDir(new File(projectDir, 'images/minimal'))
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
"""
        when:
        GradleInvocationResult result = runTasks('inspectImage')

        then:
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can build an image, create and start a container"() {
        File imageDir = createDir(new File(projectDir, 'images/minimal'))
        createDockerfile(imageDir)

        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer

task buildImage(type: DockerBuildImage) {
    inputDir = file('images/minimal')
    tag = "${createUniqueImageId()}"
}

task createContainer(type: DockerCreateContainer) {
    dependsOn buildImage
    targetImageId { buildImage.getImageId() }
}

task startContainer(type: DockerStartContainer) {
    dependsOn createContainer
    targetContainerId { createContainer.getContainerId() }
    portBindings = ['8080:8080']
}
"""
        expect:
        runTasks('startContainer')
    }

    def "Can push latest container"() {
        buildFile << """
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

task createContainer(type: DockerCreateContainer) {
    imageId = 'busybox'
    cmd = ['true'] as String[]
}

task commitImage(type: DockerCommitImage) {
    dependsOn createContainer
    repository = 'bmuschko/busybox'
    targetContainerId { createContainer.getContainerId() }
}

task pushImage(type: DockerPushImage) {
    dependsOn commitImage
    imageName = 'bmuschko/busybox'
}

task removeImage(type: DockerRemoveImage) {
    dependsOn pushImage
    targetImageId { commitImage.getImageId() }
}
"""

        expect:
        runTasks('removeImage')
    }

    @IgnoreIf({ !AbstractIntegrationTest.hasDockerHubCredentials() })
    def "Can push image to DockerHub and pull it afterward"() {
        buildFile << """

docker {
    registry {
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
    repository = "\$docker.registry.username/busybox"
    targetContainerId { createContainer.getContainerId() }
}

task pushImage(type: DockerPushImage) {
    dependsOn commitImage
    imageName = "\$docker.registry.username/busybox"
}

task pullImage(type: DockerPullImage) {
    dependsOn pushImage
    repository = "\$docker.registry.username/busybox"
}
"""

        expect:
        runTasks('pullImage')
    }

    private File createDockerfile(File imageDir) {
        File dockerFile = new File(imageDir, 'Dockerfile')

        FileUtils.writeStringToFile(dockerFile, """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
""")
        dockerFile
    }
}
