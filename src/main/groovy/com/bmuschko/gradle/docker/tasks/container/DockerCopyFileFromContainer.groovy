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

import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd
import groovy.io.FileType
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import java.util.stream.Stream

@CompileStatic
class DockerCopyFileFromContainer extends DockerExistingContainer {
    /**
     * Path inside container
     */
    @Input
    final Property<String> remotePath = project.objects.property(String)

    /**
     * Path on host to write remotePath to or into.
     *
     * If hostPath does not exist it will be created relative to
     * what we need it to be (e.g. regular file or directory).
     * This is consistent with how 'docker cp' behaves.
     */
    @Input
    final Property<String> hostPath = project.objects.property(String)

    /**
     * Whether to leave file in its compressed state or not.
     *
     * Docker CP command hands back a tar stream regardless if we asked
     * for a regular file or directory. Thus, we can give the caller
     * back the tar file as-is or explode it to some destination like
     * 'docker cp' does.
     */
    @Input
    @Optional
    final Property<Boolean> compressed = project.objects.property(Boolean)

    private final FileSystemOperations fileSystemOperations

    private final ArchiveOperations archiveOperations

    @Inject
    DockerCopyFileFromContainer(FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations) {
        hostPath.convention(project.projectDir.path)
        compressed.convention(false)
        this.fileSystemOperations = fileSystemOperations
        this.archiveOperations = archiveOperations
    }

    @Override
    void runRemoteCommand() {

        CopyArchiveFromContainerCmd containerCommand = dockerClient.copyArchiveFromContainerCmd(containerId.get(), remotePath.get())
        logger.quiet "Copying '${remotePath.get()}' from container with ID '${containerId.get()}' to '${hostPath.get()}'."

        InputStream tarStream
        try {
            tarStream = containerCommand.exec()

            if(nextHandler) {
                nextHandler.execute(tarStream)
            } else {
                def hostDestination = Paths.get(hostPath.get())

                // if compressed leave file as is otherwise untar
                if (compressed.getOrNull()) {
                    copyFileCompressed(tarStream, hostDestination)
                } else {
                    copyFile(tarStream, hostDestination)
                }
            }
        } finally {
            tarStream?.close()
        }
    }

    /**
     * Copy tar-stream from container to host
     */
    private void copyFileCompressed(InputStream tarStream, Path hostDestination) {

        // If user supplied an existing directory then we are responsible for naming and so
        // will ensure file ends with '.tar'. If user supplied a regular file then use
        // whichever name was passed in.
        def fileName = remotePath.get()
        def compressedFileName = (Files.exists(hostDestination) && Files.isDirectory(hostDestination)) ?
                (fileName.endsWith(".tar") ? fileName : fileName + ".tar") :
                hostDestination.fileName.toString()

        def compressedFileLocation = (Files.exists(hostDestination) && Files.isDirectory(hostDestination)) ?
                hostDestination :
                hostDestination.parent

        // If user supplied regular file ensure its parent location exists and if
        // the regular file itself exists, delete to avoid clobbering.
        if (Files.exists(hostDestination)) {
            if (!Files.isDirectory(hostDestination)) {
                Files.delete(hostDestination)
            }
        } else {
            if (!Files.exists(hostDestination.parent)) {
                Files.createDirectories(hostDestination.parent);
            }
        }

        Path tarFile = compressedFileLocation.resolve(compressedFileName)
        try (OutputStream it = Files.newOutputStream(tarFile)) {
            it << tarStream
        }
    }

    /**
     * Copy regular file or directory from container to host
     */
    private void copyFile(InputStream tarStream, Path hostDestination) {

        def tempDestination = untarStream(tarStream)

        /*
            At this juncture we have 3 possibilities:

                1.) 0 files were found in which case we do nothing

                2.) 1 regular file was found

                3.) N regular files (and possibly directories) were found
        */
        def fileCount = 0
        tempDestination.eachFileRecurse(FileType.FILES) { fileCount++ }
        if (fileCount == 0) {
            logger.quiet "Nothing to copy."
        } else if (fileCount == 1) {
            copySingleFile(hostDestination, tempDestination)
        } else {
            copyMultipleFiles(hostDestination, tempDestination)
        }
    }

    /**
     * Unpack tar stream into generated directory relative to $buildDir
     */
    private Path untarStream(InputStream tarStream) {

        // Write tar to temp location since we are exploding it anyway
        Path tempFile = temporaryDir.toPath().resolve(UUID.randomUUID().toString() + ".tar")
        try (OutputStream it = Files.newOutputStream(tempFile)) {
            it << tarStream
        }

        // We are not allowed to rename tempDir's created in OS temp directory (as
        // we do further downstream) which is why we are creating via our task's
        // temporaryDir
        def outputDirectory = temporaryDir.toPath().resolve(UUID.randomUUID().toString())
        Files.createDirectories(outputDirectory)

        FileTree tarTree = archiveOperations.tarTree(tempFile)
        fileSystemOperations.copy(new Action<CopySpec>() {
            @Override
            void execute(CopySpec copySpec) {
                copySpec.into(outputDirectory)
                copySpec.from(tarTree)
            }
        })
        return outputDirectory
    }

    /**
     * Copy regular file inside tempDestination to, or into, hostDestination
     */
    private void copySingleFile(Path hostDestination, Path tempDestination) {

        // ensure regular file does not exist as we don't want clobbering
        if (Files.exists(hostDestination) && !Files.isDirectory(hostDestination)) {
            Files.delete(hostDestination)
        }

        // create parent files of hostPath should they not exist
        if (!Files.exists(hostDestination) && !Files.exists(hostDestination.parent)) {
            Files.createDirectories(hostDestination.parent)
        }

        def parentDirectory = Files.isDirectory(hostDestination) ? hostDestination : hostDestination.parent
        List<Path> files
        try (def stream = Files.list(tempDestination)) {
            files = stream.collect(Collectors.toList());
        }
        def fileName = Files.isDirectory(hostDestination) ?
            files.last().fileName : hostDestination.fileName

        Path destination = parentDirectory.resolve(fileName)
        Files.move(files.last(), destination)
    }

    /**
     * Copy files inside tempDestination into hostDestination
     */
    private void copyMultipleFiles(Path hostDestination, Path tempDestination) {

        // Flatten single top-level directory to behave more like docker. Basically
        // we are turning this:
        //
        //     /<requested-host-dir>/base-directory/actual-files-start-here
        //
        // into this:
        //
        //     /<requested-host-dir>/actual-files-start-here
        //
        // gradle does not currently offer any mechanism to do this which
        // is why we have to do the following gymnastics
        def files = Files.list(tempDestination).withCloseable { it.collect(Collectors.toList()) }
        if (files.size() == 1) {
            Path dirToFlatten = files.last()
            Path dirToFlattenParent = dirToFlatten.parent
            Path flatDir = dirToFlattenParent.resolve(UUID.randomUUID().toString())

            // rename origin to escape potential clobbering
            Files.move(dirToFlatten, flatDir)

            // rename files 1 level higher
            for (Path it : flatDir) {
                def movedFile = dirToFlattenParent.resolve(it.fileName)
                Files.move(it, movedFile)
            }

            if (!flatDir.deleteDir()) {
                throw new GradleException("Failed deleting directory at ${flatDir}")
            }
        }

        // delete regular file should it exist
        if (Files.exists(hostDestination) && !Files.isDirectory(hostDestination)) {
            Files.delete(hostDestination)
        }

        // If directory already exists, rename each file into
        // said directory, otherwise rename entire directory.
        if (Files.exists(hostDestination)) {
            def parentName = tempDestination.fileName.toString()
            tempDestination.each {
                def originPath = it.toAbsolutePath()
                def index = originPath.toString().lastIndexOf(parentName) + parentName.length()
                def relativePath = originPath.toString().substring(index, originPath.toString().length())
                def destFile = Paths.get("${hostDestination}/${relativePath}")
                Files.move(it, destFile)
            }
        } else {
            Files.move(tempDestination, hostDestination)
        }
    }
}
