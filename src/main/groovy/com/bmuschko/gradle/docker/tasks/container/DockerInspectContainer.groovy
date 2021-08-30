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

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.VolumeBind
import com.github.dockerjava.api.model.VolumesFrom
import groovy.transform.CompileStatic
import org.gradle.api.Action

@CompileStatic
class DockerInspectContainer extends DockerExistingContainer {

    DockerInspectContainer() {
        defaultResponseHandling()
    }

    @Override
    void runRemoteCommand() {
        logger.quiet "Inspecting container with ID '${containerId.get()}'."
        InspectContainerResponse container = dockerClient.inspectContainerCmd(containerId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(container)
        }
    }

    private void defaultResponseHandling() {
        Action<InspectContainerResponse> action = new Action<InspectContainerResponse>() {
            @Override
            void execute(InspectContainerResponse container) {
                logger.quiet "Image ID    : $container.imageId"
                logger.quiet "Name        : $container.name"
                logger.quiet "Links       : $container.hostConfig.links"
                VolumeBind[] volumes = container.volumes ? container.volumes : new VolumeBind[0]
                logger.quiet "Volumes     : $volumes"
                VolumesFrom[] volumesFrom = container.hostConfig.volumesFrom ? container.hostConfig.volumesFrom : new VolumesFrom[0]
                logger.quiet "VolumesFrom : $volumesFrom"
                ExposedPort[] exposedPorts = container.config.exposedPorts ? container.config.exposedPorts : new ExposedPort[0]
                logger.quiet "ExposedPorts : $exposedPorts"
                logger.quiet "LogConfig : $container.hostConfig.logConfig.type.type"
                logger.quiet "RestartPolicy : $container.hostConfig.restartPolicy"
                logger.quiet "PidMode : $container.hostConfig.pidMode"
                List<String> devices = container.hostConfig.devices ?
                    container.hostConfig.devices.collect {
                        "${it.pathOnHost}:${it.pathInContainer}:${it.cGroupPermissions}".toString()
                    } : []
                logger.quiet "Devices : $devices"
                logger.quiet "TmpFs : $container.hostConfig.tmpFs"
            }
        }

        onNext(action)
    }
}
