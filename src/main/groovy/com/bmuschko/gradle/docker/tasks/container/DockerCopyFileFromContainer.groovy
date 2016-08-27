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

import groovy.io.FileType
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCopyFileFromContainer extends DockerExistingContainer {
    /**
     * Path inside container
     */
    @Input
    String remotePath

    /**
     * Path on host to write remotePath to or into.
     *
     * If hostPath does not exist it will be created relative to
     * what we need it to be (e.g. regular file or directory).
     * This is consistent with how 'docker cp' behaves.
     */
    @Input
    String hostPath = project.projectDir.path

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
    Boolean compressed = Boolean.FALSE

    @Override
    void runRemoteCommand(dockerClient) {

        def containerCommand = dockerClient.copyArchiveFromContainerCmd(getContainerId(), getRemotePath())
        logger.quiet "Copying '${getRemotePath()}' from container with ID '${getContainerId()}' to '${getHostPath()}'."

        def tarStream
        try {

            tarStream = containerCommand.exec()
            def hostDestination = new File(hostPath)

            // if compressed leave file as is otherwise untar
            if (compressed) {
                copyFileCompressed(tarStream, hostDestination)
            } else {
                copyFile(tarStream, hostDestination)
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
        def fileName = new File(getRemotePath()).name
        def compressedFileName = (hostDestination.exists() && hostDestination.isDirectory()) ?
                (fileName.endsWith(".tar") ?: fileName + ".tar") :
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

        def tempDestination
        try {

            tempDestination = untarStream(tarStream)

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
        } finally {
            if(!tempDestination?.deleteDir())
                throw new GradleException("Failed deleting directory at ${tempDestination.path}")
        }
    }

    /**
     * Unpack tar stream into generated directory relative to $buildDir
     */
    private File untarStream(InputStream tarStream) {

        def tempFile
        def outputDirectory
        try {

            // Write tar to temp location since we are exploding it anyway
            tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tar")
            tempFile.withOutputStream { it << tarStream }

            // We are not allowed to rename tempDir's created in OS temp directory (as
            // we do further downstream) which is why we are creating it relative to
            // build directory
            outputDirectory = new File("${project.buildDir}/${UUID.randomUUID().toString()}")
            if(!outputDirectory.mkdirs())
                throw new GradleException("Failed creating directory at ${outputDirectory.path}")

            project.copy {
                into outputDirectory
                from project.tarTree(tempFile)
            }
            return outputDirectory

        } finally {
            if(!tempFile.delete())
                throw new GradleException("Failed deleting previously existing file at ${tempFile.path}")
        }
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
