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
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
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
    @Optional
    File hostPath = project.projectDir

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
        def containerCommand = dockerClient.copyFileFromContainerCmd(getContainerId(), getRemotePath())
        logger.quiet "Copying '${getRemotePath()}' from container with ID '${getContainerId()}' to '${getHostPath()}'."

        def tarStream
        try {

            tarStream = containerCommand.exec()

            // if compressed leave file as is otherwise untar
            if (compressed) {
                copyFileCompressed(tarStream)
            } else {
                copyFile(tarStream)
            }
        } finally {
            tarStream?.close()
        }
    }

    /**
     * Copy tar-stream from container to host
     */
    private void copyFileCompressed(InputStream tarStream) {

        // If user supplied an existing directory then we are responsible for naming and so
        // will ensure file ends with '.tar'. If user supplied a regular file then use
        // whichever name was passed in.
        def fileName = new File(getRemotePath()).name
        def compressedFileName = (hostPath.exists() && hostPath.isDirectory()) ?
                (fileName.endsWith(".tar") ?: fileName + ".tar") :
                hostPath.name

        def compressedFileLocation = (hostPath.exists() && hostPath.isDirectory()) ?
                hostPath :
                hostPath.parentFile

        // If user supplied regular file ensure its parent location exists and if
        // the regular file itself exists, delete to avoid clobbering.
        if (hostPath.exists()) {
            if (!hostPath.isDirectory())
                hostPath.delete()
        } else {
            hostPath.parentFile.mkdirs()
        }

        new File(compressedFileLocation,
                compressedFileName).
                withOutputStream { it << tarStream }

    }

    /**
     * Copy regular file or directory from container to host
     */
    private void copyFile(InputStream tarStream) {

        def tempFile
        def tempDir
        try {

            // Write tar to temp location since we are exploding it anyway
            tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tar")
            tempFile.withOutputStream { it << tarStream }

            // We are not allowed to rename tempDir's created in OS temp directory (as
            // we do further downstream) which is why we are creating it relative to
            // build directory
            tempDir = new File("${project.buildDir}/${UUID.randomUUID().toString()}")
            tempDir.mkdirs()
            project.copy {
                into tempDir
                from project.tarTree(tempFile)
            }
            tempFile.delete()

            /*
                At this juncture we have 3 possibilities:

                    1.) 0 files were found in which case we do nothing

                    2.) 1 regular file was found

                    3.) N regular files (and possibly directories) were found
            */
            def fileCount = 0
            tempDir.eachFileRecurse(FileType.FILES) { fileCount++ }
            if (fileCount == 0) {

                logger.quiet "Nothing to copy."

            } else if (fileCount == 1) {

                // ensure regular file does not exist as we don't want clobbering
                if (hostPath.exists() && !hostPath.isDirectory())
                    hostPath.delete()

                // create parent files of hostPath should they not exist
                if (!hostPath.exists())
                    hostPath.parentFile.mkdirs()

                def parentDirectory = hostPath.isDirectory() ?: hostPath.parentFile
                def fileName = hostPath.isDirectory() ?
                        tempDir.listFiles().last().name : hostPath.name

                def destination = new File(parentDirectory, fileName)
                if (!tempDir.listFiles().last().renameTo(destination))
                    throw new GradleException("Failed to write file to ${destination}")

            } else {

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
                if (tempDir.listFiles().size() == 1) {
                    def dirToFlatten = tempDir.listFiles().last()
                    def dirToFlattenParent = tempDir.listFiles().last().parentFile
                    def flatDir = new File(dirToFlattenParent, UUID.randomUUID().toString())

                    // rename origin to escape potential clobbering
                    dirToFlatten.renameTo(flatDir)

                    // rename files 1 level higher
                    flatDir.listFiles().each {
                        it.renameTo(new File(dirToFlattenParent, it.name))
                    }
                    flatDir.deleteDir()
                }

                // delete regular file should it exist
                if (hostPath.exists() && !hostPath.isDirectory())
                    hostPath.delete()

                // If directory already exists, rename each file into
                // said directory, otherwise rename entire directory.
                if (hostPath.exists()) {
                    def parentName = tempDir.name
                    tempDir.listFiles().each {
                        def originPath = it.absolutePath
                        def index = originPath.lastIndexOf(parentName) + parentName.length()
                        def relativePath = originPath.substring(index, originPath.length())
                        def destFile = new File("${hostPath.absolutePath}/${relativePath}")
                        if (!it.renameTo(destFile))
                            throw new GradleException("Failed renaming file ${it} to ${destFile}")
                    }
                } else {
                    if (!tempDir.renameTo(hostPath))
                        throw new GradleException("Failed renaming file ${tempDir} to ${hostPath}")
                }
            }
        } finally {
            // ensure we delete in-case exception was thrown
            tempFile?.delete()
            tempDir?.deleteDir()
        }
    }
}
