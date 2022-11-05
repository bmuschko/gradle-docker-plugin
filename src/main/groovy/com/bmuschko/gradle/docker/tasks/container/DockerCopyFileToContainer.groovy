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

import com.bmuschko.gradle.docker.domain.CopyFileToContainer
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCopyFileToContainer extends DockerExistingContainer {
    /**
     * Path of file inside container
     */
    @Input
    @Optional
    final Property<String> remotePath = project.objects.property(String)

    /**
     * File path on host to copy into container
     */
    @Input
    @Optional
    final Property<String> hostPath = project.objects.property(String)

    /**
     * Tar file we will copy into container
     */
    @Input
    @Optional
    final RegularFileProperty tarFile = project.objects.fileProperty()

    @Input
    @Optional
    final List<CopyFileToContainer> copyFiles = []

    @Override
    void runRemoteCommand() {

        if (remotePath.getOrNull()) {
            if (hostPath.getOrNull() && tarFile.getOrNull()) {
                throw new GradleException("Can specify either hostPath or tarFile not both")
            } else {
                if (hostPath.getOrNull()) {
                    withFile(hostPath.get(), remotePath.get())
                } else if (tarFile.getOrNull()) {
                    withTarFile(tarFile.get(), remotePath.get())
                }
            }
        }

        for (int i = 0; i < copyFiles.size(); i++) {
            CopyFileToContainer fileToCopy = copyFiles.get(i)
            logger.quiet "Copying file to container with ID '${containerId.get()}' at '${fileToCopy.remotePath}'."
            CopyArchiveToContainerCmd containerCommand = dockerClient.copyArchiveToContainerCmd(containerId.get())
            setContainerCommandConfig(containerCommand, fileToCopy)
            containerCommand.exec()
        }
    }

    private final fileOperations = (project as ProjectInternal).fileOperations

    private void setContainerCommandConfig(CopyArchiveToContainerCmd containerCommand, CopyFileToContainer copyFileToContainer) {

        def localHostPath
        if (copyFileToContainer.hostPath instanceof Closure) {
            localHostPath = fileOperations.file(copyFileToContainer.hostPath.call())
        } else {
            localHostPath = fileOperations.file(copyFileToContainer.hostPath)
        }

        def localRemotePath
        if (copyFileToContainer.remotePath instanceof Closure) {
            containerCommand.withRemotePath(copyFileToContainer.remotePath.call() as String)
        } else {
            containerCommand.withRemotePath(copyFileToContainer.remotePath as String)
        }

        if (copyFileToContainer.isTar) {
            containerCommand.withTarInputStream(localHostPath.newInputStream())
        } else {
            containerCommand.withHostResource(localHostPath.path)
        }
    }

    /**
     * Add a file to be copied into container
     *
     * @param hostPath can be either String, GString, File or Closure which returns any of the previous.
     * @param remotePath can be either String, GString, File or Closure which returns any of the previous.
     */
    void withFile(def hostPath, def remotePath) {
        copyFiles << new CopyFileToContainer(hostPath: Objects.requireNonNull(hostPath),
            remotePath: Objects.requireNonNull(remotePath))
    }

    /**
     * Add a tarfile to be copied into container
     *
     * @param hostPath can be either String, GString, File or Closure which returns any of the previous.
     * @param remotePath can be either String, GString, File or Closure which returns any of the previous.
     */
    void withTarFile(def hostPath, def remotePath) {
        copyFiles << new CopyFileToContainer(hostPath: Objects.requireNonNull(hostPath),
            remotePath: Objects.requireNonNull(remotePath),
            isTar: true)
    }
}
