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

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.GradleInvocationResult
import com.bmuschko.gradle.docker.ToolingApiIntegrationTest
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
}
"""
        expect:
        runTasks('startContainer')
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
