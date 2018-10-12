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

    DockerInspectContainer() {
        defaultResponseHandling()
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Inspecting container with ID '${containerId.get()}'."
        def container = dockerClient.inspectContainerCmd(containerId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(container)
        }
    }

    private void defaultResponseHandling() {
        Closure c = { container ->
            logger.quiet "Image ID    : $container.imageId"
            logger.quiet "Name        : $container.name"
            logger.quiet "Links       : $container.hostConfig.links"
            def volumes = container.@volumes ? container.volumes : '[]'
            logger.quiet "Volumes     : $volumes"
            def volumesFrom = container.hostConfig.@volumesFrom ? container.hostConfig.volumesFrom : '[]'
            logger.quiet "VolumesFrom : $volumesFrom"
            String exposedPorts = container.config.exposedPorts ? container.config.exposedPorts.toString() : '[]'
            logger.quiet "ExposedPorts : $exposedPorts"
            logger.quiet "LogConfig : $container.hostConfig.logConfig.type.type"
            logger.quiet "RestartPolicy : $container.hostConfig.restartPolicy"
            logger.quiet "PidMode : $container.hostConfig.pidMode"
            def devices = container.hostConfig.@devices ?
                container.hostConfig.devices.collect {
                    "${it.pathOnHost}:${it.pathInContainer}:${it.cGroupPermissions}"
                } : '[]'
            logger.quiet "Devices : $devices"
        }

        nextHandler = c
    }
}
