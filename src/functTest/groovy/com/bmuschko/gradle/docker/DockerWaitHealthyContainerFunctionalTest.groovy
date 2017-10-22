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

class DockerWaitHealthyContainerFunctionalTest extends AbstractFunctionalTest {

    def "Wait for a container to be healthy"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*
            import com.bmuschko.gradle.docker.tasks.container.extras.*

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                instruction { "HEALTHCHECK --interval=1s CMD test -e /tmp/HEALTHY || exit 1" }
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

            task waitContainer(type: DockerWaitHealthyContainer) {
                dependsOn startContainer
                targetContainerId { startContainer.getContainerId() }
                timeout 10
            }
        """

        expect:
        build('waitContainer')
    }
}
