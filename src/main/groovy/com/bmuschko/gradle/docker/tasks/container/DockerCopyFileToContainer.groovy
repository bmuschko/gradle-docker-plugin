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

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCopyFileToContainer extends DockerExistingContainer {
    /**
     * Path of file inside container
     */
    @Input
    @Optional
    String remotePath

    /**
     * File path on host to copy into container
     */
    @Input
    @Optional
    String hostPath

    /**
     * Tar file we will copy into container
     */
    @Input
    @Optional
    Closure<File> tarFile

    @Override
    void runRemoteCommand(dockerClient) {    
        logger.quiet "Copying file to container with ID '${getContainerId()}' at '${getRemotePath()}'."
        def containerCommand = dockerClient.copyArchiveToContainerCmd(getContainerId())
        setContainerCommandConfig(containerCommand)
        containerCommand.exec()
    }

    private void setContainerCommandConfig(containerCommand) {
        if (getRemotePath()) {
            containerCommand.withRemotePath(getRemotePath())
        }
                
        if (getHostPath() && getTarFile()) {
            throw new GradleException("Can specify either hostPath or tarFile not both")
        }
                
        if (getHostPath()) {
            containerCommand.withHostResource(getHostPath())
        }

        if (getTarFile()) {
            containerCommand.withTarInputStream(getTarFile().call().newInputStream())
        }
    }
}
