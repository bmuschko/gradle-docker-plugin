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
package org.gradle.api.plugins.docker.tasks.container

import org.gradle.api.plugins.docker.tasks.AbstractDockerTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCreateContainer extends AbstractDockerTask {
    @Input
    String imageId

    @Input
    @Optional
    String hostName

    @Input
    @Optional
    String[] portSpecs

    @Input
    @Optional
    String user

    @Input
    @Optional
    Boolean stdinOpen

    @Input
    @Optional
    Boolean stdinOnce

    @Input
    @Optional
    Long memoryLimit

    @Input
    @Optional
    Long memorySwap

    @Input
    @Optional
    Boolean attachStdin

    @Input
    @Optional
    Boolean attachStdout

    @Input
    @Optional
    Boolean attachStderr

    @Input
    @Optional
    String[] env

    @Input
    @Optional
    String[] cmd

    @Input
    @Optional
    String[] dns

    @Input
    @Optional
    String image

    @Input
    @Optional
    String volumesFrom

    @Input
    @Optional
    String workingDir

    @Input
    @Optional
    Map<String, ?> exposedPorts

    private String containerId

    DockerCreateContainer() {
        ext.getContainerId = { containerId }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        def containerCommand = dockerClient.createContainerCmd(getImageId())
        setContainerCommandConfig(containerCommand)
        def container = containerCommand.exec()
        logger.quiet "Created container with ID '$container.id'."
        containerId = container.id
    }

    private void setContainerCommandConfig(containerCommand) {
        if(getHostName()) {
            containerCommand.withHostName(getHostName())
        }

        if(getPortSpecs()) {
            containerCommand.withPortSpecs(getPortSpecs())
        }

        if(getUser()) {
            containerCommand.withUser(getUser())
        }

        if(getStdinOpen()) {
            containerCommand.withStdinOpen(getStdinOpen())
        }

        if(getStdinOnce()) {
            containerCommand.withStdInOnce(getStdinOnce())
        }

        if(getMemoryLimit()) {
            containerCommand.withMemoryLimit(getMemoryLimit())
        }

        if(getMemorySwap()) {
            containerCommand.withMemorySwap(getMemorySwap())
        }

        if(getAttachStdin()) {
            containerCommand.withAttachStdin(getAttachStdin())
        }

        if(getAttachStdout()) {
            containerCommand.withAttachStdout(getAttachStdout())
        }

        if(getAttachStderr()) {
            containerCommand.withAttachStderr(getAttachStderr())
        }

        if(getEnv()) {
            containerCommand.withEnv(getEnv())
        }

        if(getCmd()) {
            containerCommand.withCmd(getCmd())
        }

        if(getDns()) {
            containerCommand.withDns(getDns())
        }

        if(getImage()) {
            containerCommand.withImage(getImage())
        }

        if(getVolumesFrom()) {
            containerCommand.withVolumesFrom(getVolumesFrom())
        }

        if(getWorkingDir()) {
            containerCommand.withWorkingDir(getWorkingDir())
        }

        if(getExposedPorts()) {
            containerCommand.withExposedPorts(getExposedPorts())
        }
    }
}

