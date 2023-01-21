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
package com.bmuschko.gradle.docker.tasks.container;

import com.bmuschko.gradle.docker.domain.CopyFileToContainer;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;

public class DockerCopyFileToContainer extends DockerExistingContainer {
    /**
     * Path of file inside container
     */
    @Input
    @Optional
    public final Property<String> getRemotePath() {
        return remotePath;
    }

    /**
     * File path on host to copy into container
     */
    @Input
    @Optional
    public final Property<String> getHostPath() {
        return hostPath;
    }

    /**
     * Tar file we will copy into container
     */
    @InputFile
    @Optional
    public final RegularFileProperty getTarFile() {
        return tarFile;
    }

    @Input
    @Optional
    public final ArrayList<CopyFileToContainer> getCopyFiles() {
        return copyFiles;
    }

    private final Property<String> remotePath = getProject().getObjects().property(String.class);
    private final Property<String> hostPath = getProject().getObjects().property(String.class);
    private final RegularFileProperty tarFile = getProject().getObjects().fileProperty();
    private final ArrayList<CopyFileToContainer> copyFiles = new ArrayList<CopyFileToContainer>();

    @Override
    public void runRemoteCommand() throws IOException {

        if (remotePath.getOrNull() != null) {
            if (hostPath.getOrNull() != null && tarFile.getOrNull() != null) {
                throw new GradleException("Can specify either hostPath or tarFile not both");
            } else {
                if (hostPath.getOrNull() != null) {
                    withFile(hostPath.get(), remotePath.get());
                } else if (tarFile.getOrNull() != null) {
                    withTarFile(tarFile.get(), remotePath.get());
                }
            }
        }

        for (int i = 0; i < copyFiles.size(); i++) {
            final CopyFileToContainer fileToCopy = copyFiles.get(i);
            getLogger().quiet("Copying file to container with ID '" + getContainerId().get() + "' at '" + String.valueOf(fileToCopy.getRemotePath()) + "'.");
            CopyArchiveToContainerCmd containerCommand = getDockerClient().copyArchiveToContainerCmd(getContainerId().get());
            setContainerCommandConfig(containerCommand, fileToCopy);
            containerCommand.exec();
        }
    }

    private final FileOperations fileOperations = ((ProjectInternal)getProject()).getFileOperations();

    private void setContainerCommandConfig(CopyArchiveToContainerCmd containerCommand, CopyFileToContainer copyFileToContainer) throws IOException {

        File localHostPath;
        if (copyFileToContainer.getHostPath() instanceof Closure) {
            localHostPath = fileOperations.file(((Closure)copyFileToContainer.getHostPath()).call());
        } else {
            localHostPath = fileOperations.file(copyFileToContainer.getHostPath());
        }

        if (copyFileToContainer.getRemotePath() instanceof Closure) {
            containerCommand.withRemotePath((String) ((Closure)copyFileToContainer.getRemotePath()).call());
        } else {
            containerCommand.withRemotePath((String)copyFileToContainer.getRemotePath());
        }

        if (copyFileToContainer.getIsTar()) {
            containerCommand.withTarInputStream(Files.newInputStream(localHostPath.toPath()));
        } else {
            containerCommand.withHostResource(localHostPath.getPath());
        }
    }

    /**
     * Add a file to be copied into container
     *
     * @param hostPath   can be either String, GString, File or Closure which returns any of the previous.
     * @param remotePath can be either String, GString, File or Closure which returns any of the previous.
     */
    public void withFile(Object hostPath, Object remotePath) {
        CopyFileToContainer container = new CopyFileToContainer();
        container.setHostPath(Objects.requireNonNull(hostPath));
        container.setRemotePath(Objects.requireNonNull(remotePath));

        copyFiles.add(container);
    }

    /**
     * Add a tarfile to be copied into container
     *
     * @param hostPath   can be either String, GString, File or Closure which returns any of the previous.
     * @param remotePath can be either String, GString, File or Closure which returns any of the previous.
     */
    public void withTarFile(Object hostPath, Object remotePath) {
        CopyFileToContainer container = new CopyFileToContainer();
        container.setHostPath(Objects.requireNonNull(hostPath));
        container.setRemotePath(Objects.requireNonNull(remotePath));
        container.setIsTar(true);

        copyFiles.add(container);
    }
}
