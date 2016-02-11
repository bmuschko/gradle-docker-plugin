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

class DockerInspectContainer extends DockerExistingContainer {

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Inspecting container with ID '${getContainerId()}'."
        def command = dockerClient.inspectContainerCmd(getContainerId())
        configureExecutionHandler()(command)
    }

    private Closure configureExecutionHandler() {
        if(!getExecutionHandler()) {
            setExecutionHandler { command ->
                def container = command.exec()
                logger.quiet "Image ID    : $container.imageId"
                logger.quiet "Name        : $container.name"
                logger.quiet "Links       : $container.hostConfig.links"
                logger.quiet "Volumes     : $container.volumes"
                logger.quiet "VolumesFrom : $container.hostConfig.volumesFrom"
                String exposedPorts = container.config?.@exposedPorts ? container.config.exposedPorts : '[]'
                logger.quiet "ExposedPorts : $exposedPorts"
            }
        }
        getExecutionHandler()
    }
}
