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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import java.lang.reflect.Constructor

class DockerCreateContainer extends AbstractDockerRemoteApiTask {
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
    String[] volumes

    @Input
    @Optional
    String[] volumesFrom

    @Input
    @Optional
    String workingDir

    @Input
    @Optional
    Map<String, Integer> exposedPorts

    String containerId

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

    void targetImageId(Closure imageId) {
        conventionMapping.imageId = imageId
    }

    @Input
    String getImageId() {
        imageId
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

        if(getVolumes()) {
            def createdVolumes = getVolumes().collect { createVolume(it) }
            containerCommand.withVolumes(createVolumes(createdVolumes))
        }

        if(getVolumesFrom()) {
            containerCommand.withVolumesFrom(getVolumesFrom())
        }

        if(getWorkingDir()) {
            containerCommand.withWorkingDir(getWorkingDir())
        }

        if(getExposedPorts()) {
            def createdExposedPorts = getExposedPorts().collect { createExposedPort(it.key, it.value) }
            containerCommand.withExposedPorts(createExposedPorts(createdExposedPorts))
        }
    }

    def createVolume(String path) {
        Class volumeClass = threadContextClassLoader.loadClass('com.github.dockerjava.api.model.Volume')
        Constructor constructor = volumeClass.getConstructor(String)
        constructor.newInstance(path)
    }

    def createVolumes(volumes) {
        Class volumesClass = threadContextClassLoader.loadClass('com.github.dockerjava.api.model.Volumes')
        Constructor constructor = volumesClass.getConstructor(Object[])
        constructor.newInstance(volumes)
    }

    def createExposedPort(String scheme, Integer port) {
        Class exposedPortClass = threadContextClassLoader.loadClass('com.github.dockerjava.api.model.ExposedPort')
        Constructor constructor = exposedPortClass.getConstructor(String, Integer)
        constructor.newInstance(scheme, port)
    }

    def createExposedPorts(exposedPorts) {
        Class exposedPortsClass = threadContextClassLoader.loadClass('com.github.dockerjava.api.model.ExposedPorts')
        Constructor constructor = exposedPortsClass.getConstructor(Object[])
        constructor.newInstance(exposedPorts)
    }
}

