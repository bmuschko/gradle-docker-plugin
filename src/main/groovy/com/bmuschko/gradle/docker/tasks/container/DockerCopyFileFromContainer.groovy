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
                def hostDestination = new File(hostPath.get())

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
    private void copyFileCompressed(InputStream tarStream, File hostDestination) {

        // If user supplied an existing directory then we are responsible for naming and so
        // will ensure file ends with '.tar'. If user supplied a regular file then use
        // whichever name was passed in.
        def fileName = new File(remotePath.get()).name
        def compressedFileName = (hostDestination.exists() && hostDestination.isDirectory()) ?
                (fileName.endsWith(".tar") ? fileName : fileName + ".tar") :
                hostDestination.name

        def compressedFileLocation = (hostDestination.exists() && hostDestination.isDirectory()) ?
                hostDestination :
                hostDestination.parentFile

        // If user supplied regular file ensure its parent location exists and if
        // the regular file itself exists, delete to avoid clobbering.
        if (hostDestination.exists()) {
            if (!hostDestination.isDirectory())
                if(!hostDestination.delete())
                    throw new GradleException("Failed deleting previously existing file at ${hostDestination.path}")

        } else {
            if (!hostDestination.parentFile.exists() && !hostDestination.parentFile.mkdirs())
                throw new GradleException("Failed creating parent directory for ${hostDestination.path}")
        }

        def tarFile = new File(compressedFileLocation, compressedFileName)
        if(!tarFile.createNewFile())
            throw new GradleException("Failed creating file at ${tarFile.path}")
        else
            tarFile.withOutputStream { it << tarStream }
    }

    /**
     * Copy regular file or directory from container to host
     */
    private void copyFile(InputStream tarStream, File hostDestination) {

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
    private File untarStream(InputStream tarStream) {

        // Write tar to temp location since we are exploding it anyway
        def tempFile = new File(temporaryDir, UUID.randomUUID().toString() + ".tar")
        tempFile.withOutputStream { it << tarStream }

        // We are not allowed to rename tempDir's created in OS temp directory (as
        // we do further downstream) which is why we are creating via our task's
        // temporaryDir
        def outputDirectory = new File(temporaryDir, UUID.randomUUID().toString())
        if(!outputDirectory.mkdirs())
            throw new GradleException("Failed creating directory at ${outputDirectory.path}")

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
    private void copySingleFile(File hostDestination, File tempDestination) {

        // ensure regular file does not exist as we don't want clobbering
        if (hostDestination.exists() && !hostDestination.isDirectory())
            if(!hostDestination.delete())
                throw new GradleException("Failed deleting file at ${hostDestination.path}")

        // create parent files of hostPath should they not exist
        if (!hostDestination.exists())
            if(!hostDestination.parentFile.exists() && !hostDestination.parentFile.mkdirs())
                throw new GradleException("Failed creating parent directory for ${hostDestination.path}")

        def parentDirectory = hostDestination.isDirectory() ? hostDestination : hostDestination.parentFile
        def fileName = hostDestination.isDirectory() ?
                tempDestination.listFiles().last().name : hostDestination.name

        def destination = new File(parentDirectory, fileName)
        if (!tempDestination.listFiles().last().renameTo(destination))
            throw new GradleException("Failed renaming file ${tempDestination.path} to ${destination.path}")

    }

    /**
     * Copy files inside tempDestination into hostDestination
     */
    private void copyMultipleFiles(File hostDestination, File tempDestination) {

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
        if (tempDestination.listFiles().size() == 1) {
            def dirToFlatten = tempDestination.listFiles().last()
            def dirToFlattenParent = tempDestination.listFiles().last().parentFile
            def flatDir = new File(dirToFlattenParent, UUID.randomUUID().toString())

            // rename origin to escape potential clobbering
            if(!dirToFlatten.renameTo(flatDir))
                throw new GradleException("Failed renaming file ${dirToFlatten.path} to ${flatDir.path}")

            // rename files 1 level higher
            flatDir.listFiles().each {
                def movedFile = new File(dirToFlattenParent, it.name)
                if(!it.renameTo(movedFile))
                    throw new GradleException("Failed renaming file ${it.path} to ${movedFile.path}")
            }

            if(!flatDir.deleteDir())
                throw new GradleException("Failed deleting directory at ${flatDir.path}")
        }

        // delete regular file should it exist
        if (hostDestination.exists() && !hostDestination.isDirectory())
            if(!hostDestination.delete())
                throw new GradleException("Failed deleting file at ${hostDestination.path}")

        // If directory already exists, rename each file into
        // said directory, otherwise rename entire directory.
        if (hostDestination.exists()) {
            def parentName = tempDestination.name
            tempDestination.listFiles().each {
                def originPath = it.absolutePath
                def index = originPath.lastIndexOf(parentName) + parentName.length()
                def relativePath = originPath.substring(index, originPath.length())
                def destFile = new File("${hostDestination.path}/${relativePath}")
                if (!it.renameTo(destFile))
                    throw new GradleException("Failed renaming file ${it.path} to ${destFile.path}")
            }
        } else {
            if (!tempDestination.renameTo(hostDestination))
                throw new GradleException("Failed renaming file ${tempDestination.path} to ${hostDestination.path}")
        }
    }
}
