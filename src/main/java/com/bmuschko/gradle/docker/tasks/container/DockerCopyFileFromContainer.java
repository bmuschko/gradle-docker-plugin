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

import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import org.gradle.api.Action;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Stream;

import static com.bmuschko.gradle.docker.internal.CopyUtils.copyMultipleFiles;
import static com.bmuschko.gradle.docker.internal.CopyUtils.copySingleFile;

public class DockerCopyFileFromContainer extends DockerExistingContainer {
    /**
     * Path inside container
     */
    @Input
    public final Property<String> getRemotePath() {
        return remotePath;
    }

    /**
     * Path on host to write remotePath to or into.
     * <p>
     * If hostPath does not exist it will be created relative to
     * what we need it to be (e.g. regular file or directory).
     * This is consistent with how 'docker cp' behaves.
     */
    @Input
    public final Property<String> getHostPath() {
        return hostPath;
    }

    /**
     * Whether to leave file in its compressed state or not.
     * <p>
     * Docker CP command hands back a tar stream regardless if we asked
     * for a regular file or directory. Thus, we can give the caller
     * back the tar file as-is or explode it to some destination like
     * 'docker cp' does.
     */
    @Input
    @Optional
    public final Property<Boolean> getCompressed() {
        return compressed;
    }

    private final Property<String> remotePath;
    private final Property<String> hostPath;
    private final Property<Boolean> compressed;
    private final FileSystemOperations fileSystemOperations;
    private final ArchiveOperations archiveOperations;

    @Inject
    public DockerCopyFileFromContainer(ObjectFactory objects, ProjectLayout layout, FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations) {
        this.remotePath = objects.property(String.class);
        this.hostPath = objects.property(String.class)
                .convention(layout.getProjectDirectory().getAsFile().getPath());
        this.compressed = objects.property(Boolean.class)
                .convention(false);
        this.fileSystemOperations = fileSystemOperations;
        this.archiveOperations = archiveOperations;
    }

    @Override
    public void runRemoteCommand() throws IOException {

        CopyArchiveFromContainerCmd containerCommand = getDockerClient().copyArchiveFromContainerCmd(getContainerId().get(), remotePath.get());
        getLogger().quiet("Copying '" + getRemotePath().get() + "' from container with ID '" + getContainerId().get() + "' to '" + getHostPath().get() + "'.");

        try (InputStream tarStream = containerCommand.exec()) {

            if (getNextHandler() != null) {
                getNextHandler().execute(tarStream);
            } else {
                Path hostDestination = Paths.get(hostPath.get());

                // if compressed leave file as is otherwise untar
                if (Boolean.TRUE.equals(compressed.getOrNull())) {
                    copyFileCompressed(tarStream, hostDestination);
                } else {
                    copyFile(tarStream, hostDestination);
                }
            }
        }
    }

    /**
     * Copy tar-stream from container to host
     */
    private void copyFileCompressed(InputStream tarStream, Path hostDestination) throws IOException {

        // If user supplied an existing directory then we are responsible for naming and so
        // will ensure file ends with '.tar'. If user supplied a regular file then use
        // whichever name was passed in.
        String fileName = remotePath.get();
        String compressedFileName = (Files.exists(hostDestination) && Files.isDirectory(hostDestination)) ?
                (fileName.endsWith(".tar") ? fileName : fileName + ".tar") :
                hostDestination.getFileName().toString();

        Path compressedFileLocation = (Files.exists(hostDestination) && Files.isDirectory(hostDestination)) ?
                hostDestination :
                hostDestination.getParent();

        // If user supplied regular file ensure its parent location exists and if
        // the regular file itself exists, delete to avoid clobbering.
        if (Files.exists(hostDestination)) {
            if (!Files.isDirectory(hostDestination)) {
                Files.delete(hostDestination);
            }
        } else {
            if (!Files.exists(hostDestination.getParent())) {
                Files.createDirectories(hostDestination.getParent());
            }
        }

        Path tarFile = compressedFileLocation.resolve(compressedFileName);
        try (OutputStream out = Files.newOutputStream(tarFile)) {
            tarStream.transferTo(out);
        }
    }

    /**
     * Copy regular file or directory from container to host
     */
    private void copyFile(InputStream tarStream, Path hostDestination) throws IOException {

        Path tempDestination = untarStream(tarStream);

        /*
            At this juncture we have 3 possibilities:

                1.) 0 files were found in which case we do nothing

                2.) 1 regular file was found

                3.) N regular files (and possibly directories) were found
        */
        long fileCount;
        try (Stream<Path> stream = Files.walk(tempDestination)) {
            fileCount = stream.filter(Files::isDirectory).count();
        }
        if (fileCount == 0) {
            getLogger().quiet("Nothing to copy.");
        } else if (fileCount == 1) {
            copySingleFile(hostDestination, tempDestination);
        } else {
            copyMultipleFiles(hostDestination, tempDestination);
        }
    }

    /**
     * Unpack tar stream into generated directory relative to $buildDir
     */
    private Path untarStream(InputStream tarStream) throws IOException {

        // Write tar to temp location since we are exploding it anyway
        Path tempFile = getTemporaryDir().toPath().resolve(UUID.randomUUID().toString() + ".tar");
        try (OutputStream out = Files.newOutputStream(tempFile)) {
            tarStream.transferTo(out);
        }

        // We are not allowed to rename tempDir's created in OS temp directory (as
        // we do further downstream) which is why we are creating via our task's
        // temporaryDir
        final Path outputDirectory = getTemporaryDir().toPath().resolve(UUID.randomUUID().toString());
        Files.createDirectories(outputDirectory);

        final FileTree tarTree = archiveOperations.tarTree(tempFile);
        fileSystemOperations.copy(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec copySpec) {
                copySpec.into(outputDirectory);
                copySpec.from(tarTree);
            }

        });
        return outputDirectory;
    }
}
