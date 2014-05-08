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
package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerStartContainer extends AbstractDockerTask {
    @Input
    String imageId

    @Input
    @Optional
    String[] cmds

    @Input
    @Optional
    Boolean stdin = Boolean.FALSE

    @Input
    @Optional
    Boolean stderr = Boolean.FALSE

    @Input
    @Optional
    Integer cpuShares

    @Input
    @Optional
    Boolean stdout = Boolean.FALSE

    String containerId

    DockerStartContainer() {
        ext {
            getContainerId = {
                containerId
            }
        }
    }

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        def containerConfig = createContainerConfig(classLoader)
        def dockerClient = getDockerClient(classLoader)
        def container = dockerClient.createContainer(containerConfig)
        dockerClient.startContainer(container.id)
        logger.quiet "Started container with ID $container.id."
        containerId = container.id
    }

    def createContainerConfig(URLClassLoader classLoader) {
        Class containerConfigClass = classLoader.loadClass('com.kpelykh.docker.client.model.ContainerConfig')
        def containerConfig = containerConfigClass.newInstance()
        containerConfig.image = getImageId()

        if(getCmds()) {
            containerConfig.cmd = getCmds()
        }

        logger.info "Container configuration: $containerConfig"
        containerConfig
    }
}

