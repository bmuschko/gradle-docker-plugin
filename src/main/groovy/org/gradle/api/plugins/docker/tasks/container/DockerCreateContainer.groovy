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
    Integer cpuSwap

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
    String[] entrypoint

    @Input
    @Optional
    Boolean networkDisabled

    @Input
    @Optional
    Boolean privileged

    @Input
    @Optional
    String workingDir

    @Input
    @Optional
    String domainName

    @Input
    @Optional
    Map<String, ?> exposedPorts

    @Input
    @Optional
    String[] onBuild

    String containerId

    DockerCreateContainer() {
        ext {
            getContainerId = {
                containerId
            }
        }
    }

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        def containerConfig = createContainerConfig(classLoader)
        logger.info "Container configuration: $containerConfig"
        def dockerClient = getDockerClient(classLoader)
        try {
            def container = dockerClient.createContainer(containerConfig, containerId)
            containerId = containerId ?: container.id
            logger.quiet "Created container with ID '$containerId'"
        } catch (Exception e) {
            if (containerId && e.getCause() && e.getCause().getClass().toString() == "class com.sun.jersey.api.client.UniformInterfaceException") {
                switch (e.getCause().getResponse().getStatus()) {
                    case 409:
                        logger.quiet "Container with ID '$containerId' already exists"
                        break
                    default:
                        throw e
                }
            } else  {
                throw e
            }
        }
    }

    private createContainerConfig(URLClassLoader classLoader) {
        Class containerConfigClass = classLoader.loadClass('com.kpelykh.docker.client.model.ContainerConfig')
        def containerConfig = containerConfigClass.newInstance()
        containerConfig.image = getImageId()

        if(getHostName()) {
            containerConfig.hostName = getHostName()
        }

        if(getPortSpecs()) {
            containerConfig.portSpecs = getPortSpecs()
        }

        if(getUser()) {
            containerConfig.user = getUser()
        }

        if(getStdinOpen()) {
            containerConfig.stdinOpen = getStdinOpen()
        }

        if(getStdinOnce()) {
            containerConfig.stdinOnce = getStdinOnce()
        }

        if(getMemoryLimit()) {
            containerConfig.memoryLimit = getMemoryLimit()
        }

        if(getMemorySwap()) {
            containerConfig.memorySwap = getMemorySwap()
        }

        if(getCpuSwap()) {
            containerConfig.cpuSwap = getCpuSwap()
        }

        if(getAttachStdin()) {
            containerConfig.attachStdin = getAttachStdin()
        }

        if(getAttachStdout()) {
            containerConfig.attachStdout = getAttachStdout()
        }

        if(getAttachStderr()) {
            containerConfig.attachStderr = getAttachStderr()
        }

        if(getEnv()) {
            containerConfig.env = getEnv()
        }

        if(getCmd()) {
            containerConfig.cmd = getCmd()
        }

        if(getDns()) {
            containerConfig.dns = getDns()
        }

        if(getImage()) {
            containerConfig.image = getImage()
        }

        if(getVolumesFrom()) {
            containerConfig.volumesFrom = getVolumesFrom()
        }

        if(getEntrypoint()) {
            containerConfig.entrypoint = getEntrypoint()
        }

        if(getNetworkDisabled()) {
            containerConfig.networkDisabled = getNetworkDisabled()
        }

        if(getPrivileged()) {
            containerConfig.privileged = getPrivileged()
        }

        if(getWorkingDir()) {
            containerConfig.workingDir = getWorkingDir()
        }

        if(getDomainName()) {
            containerConfig.domainName = getDomainName()
        }

        if(getExposedPorts()) {
            containerConfig.exposedPorts = getExposedPorts()
        }

        if(getOnBuild()) {
            containerConfig.onBuild = getOnBuild()
        }

        containerConfig
    }
}

