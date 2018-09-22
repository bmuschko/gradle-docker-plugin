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
package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerWaitHealthyContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "Wait for a container to be healthy"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 5; touch /tmp/HEALTHY; sleep 60'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                timeout 10
                finalizedBy removeImage
            }
        """

        expect:
        build('waitContainer')
    }

    def "Wait for a generic container to be healthy"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task pullImage(type: DockerPullImage) {
                repository = '$AbstractGroovyDslFunctionalTest.TEST_IMAGE'
                tag = '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_TAG'
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId { pullImage.getImageId() }
                cmd = ['sleep','60']
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                timeout 10
                finalizedBy removeContainer
            }
        """

        expect:
        build('waitContainer')
    }

    def "Invoke onNext periodically passing the health status"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 5; touch /tmp/HEALTHY; sleep 60'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            ext.testOutput = 0
            
            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                checkInterval 1000
                onNext { project.testOutput++ }
                onComplete { println "Test output: \${project.testOutput}" }
                finalizedBy removeImage
            }
        """

        expect:
        BuildResult result = build('waitContainer')
        result.output ==~ /(?s).*Test output: [5-7].*/
    }

    def "Last onNext execution passes 'healthy' string on success"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 5; touch /tmp/HEALTHY; sleep 60'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            ext.latestOnNext = "unhealthy"
            
            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                onNext { project.latestOnNext = it }
                onComplete { println "Test output: \${project.latestOnNext}" }
                finalizedBy removeImage
            }
        """

        expect:
        BuildResult result = build('waitContainer')
        result.output ==~ /(?s).*Test output: healthy.*/
    }

    def "Timeout when a container takes to long to be healthy"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 10; touch /tmp/HEALTHY; sleep 60'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                timeout 5
                finalizedBy removeImage
            }
        """

        expect:
        BuildResult result = buildAndFail('waitContainer')
        result.output.contains("Health check timeout expired")
    }

    def "Fail if container stops before being healthy"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 5'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { startContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                timeout 10
                finalizedBy removeImage
            }
        """

        expect:
        BuildResult result = buildAndFail('waitContainer')
        result.output ==~ /(?s).*Container with ID '.*' is not running.*/
    }

    def "Fail if waiting for a non-running container"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$AbstractGroovyDslFunctionalTest.TEST_IMAGE_WITH_TAG'
                instruction "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1"
                defaultCommand '/bin/sh', '-c', 'sleep 10'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }

            task removeContainer(type: DockerRemoveContainer) {
                targetContainerId { createContainer.getContainerId() }
                force = true
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn removeContainer
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
                timeout 10
                finalizedBy removeImage
            }
        """

        expect:
        BuildResult result = buildAndFail('waitContainer')
        result.output ==~ /(?s).*Container with ID '.*' is not running.*/
    }
}
