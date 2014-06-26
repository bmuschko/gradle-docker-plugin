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

import java.util.Map;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

class DockerStartContainer extends DockerExistingContainer {
    @Input
    @Optional
    String[] binds;

    @Input
    @Optional
    String containerIDFile;

// #TODO: implement
//    @Input
//    @Optional
//    LxcConf[] lxcConf;

    @Input
    @Optional
    String[] links

    @Input
    @Optional
    ArrayList<Map<String, ?>> portBindings

    @Input
    @Optional
    boolean privileged

    @Input
    @Optional
    boolean publishAllPorts

    @Input
    @Optional
    String dns

    @Input
    @Optional
    String dnsSearch

    @Input
    @Optional
    String volumesFrom

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        def hostConfig = createHostConfig(classLoader)
        logger.info "Container configuration: $hostConfig"
        logger.quiet "Starting container with ID '${getContainerId()}'."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.startContainer(getContainerId(), hostConfig)
    }

    private createHostConfig(URLClassLoader classLoader) {
        Class hostConfigClass = classLoader.loadClass('com.kpelykh.docker.client.model.HostConfig')
        def hostConfig = hostConfigClass.newInstance()

        if(getBinds()) {
            hostConfig.binds = getBinds()
        }

        if(getContainerIDFile()) {
            hostConfig.containerIDFile = getContainerIDFile()
        }

        if(getLinks()) {
            hostConfig.links = getLinks()
        }

        if(getPortBindings()) {
            Class portsClass = classLoader.loadClass('com.kpelykh.docker.client.model.Ports')
            Class pClass = classLoader.loadClass('com.kpelykh.docker.client.model.Ports$Port')
            def ports = portsClass.newInstance()
            getPortBindings().each { p ->
                ports.addPort(pClass.newInstance(p.scheme, p.port, p.hostIp, p.hostPort))
            }
            hostConfig.portBindings = ports
        }

        if(getPrivileged()) {
            hostConfig.privileged = getPrivileged()
        }

        if(getPublishAllPorts()) {
            hostConfig.publishAllPorts = getPublishAllPorts()
        }

        if(getDns()) {
            hostConfig.dns = getDns()
        }

        if(getDnsSearch()) {
            hostConfig.dnsSearch = getDnsSearch()
        }

        if(getVolumesFrom()) {
            hostConfig.volumesFrom = getVolumesFrom()
        }

        hostConfig
    }
}

