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
    String resource

    /**
     * Path on disk to write resource into. If hostPath does
     * not exist it will be created relative to what we need it
     * to be (e.g. regular file or directory). This is consistent
     * with how 'docker cp' behaves.
     */
    @Input
    @Optional
    File hostPath = project.projectDir

    /**
     * Whether to leave file in its compressed state or not.
     *
     * Docker CP command hands back a tar stream regardless if we asked
     * for a regular file or directory. Thus, we can give the caller
     * back the tar file as is or explode it to some location like
     * 'docker cp' does.
     */
    @Input
    @Optional
    Boolean compressed = Boolean.FALSE

    @Override
    void runRemoteCommand(dockerClient) {
        def containerCommand = dockerClient.copyFileFromContainerCmd(getContainerId(), getResource())
        logger.quiet "Copying '${getResource()}' from container with ID '${getContainerId()}' to '${getHostPath()}'."

        def input
        def tempFile
        def tempDir
        try {

            // 1.) get tar InputStream
            input = containerCommand.exec()

            // 2.) if compressed leave file as is otherwise untar
            if (compressed) {

                // ensure file ends with '.tar' if we are responsible for naming it
                def fileName = new File(resource).name
                def compressedFileName = (hostPath.exists() && hostPath.isDirectory()) ?
                        (fileName.endsWith(".tar") ? fileName : fileName + ".tar") :
                        hostPath.name

                def compressedFileLocation = (hostPath.exists() && hostPath.isDirectory()) ?
                        hostPath :
                        hostPath.parentFile

                // ensure path does not previously exist or risk clobbering
                if (hostPath.exists()) {
                    if (!hostPath.isDirectory()) { hostPath.delete() }
                } else {
                    hostPath.parentFile.mkdirs()
                }

                new File(compressedFileLocation,
                        compressedFileName).
                        withOutputStream { it << input }

            } else {

                // 3.) write tar to temp location since we are exploding it anyway
                tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tar")
                tempFile.withOutputStream { it << input }

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
                    3.1) At this juncture we have 3 possibilities:

                        a.) 0 files were found in which case we do nothing

                        b.) 1 regular file was found

                        c.) N regular files were found

                */
                def fileCount = 0
                tempDir.eachFileRecurse(FileType.FILES) { fileCount++ }
                if (fileCount == 0) {

                    println "Nothing to copy."

                } else if (fileCount == 1) {

                    // ensure regular file does not exist as we don't want clobbering
                    if (hostPath.exists() && !hostPath.isDirectory())
                        hostPath.delete()

                    // create parent files of hostPath should they not exist
                    if (!hostPath.exists())
                        hostPath.parentFile.mkdirs()

                    def parentDirectory = hostPath.isDirectory() ?
                            hostPath : hostPath.parentFile
                    def fileName = hostPath.isDirectory() ?
                            tempDir.listFiles().last().name : hostPath.name

                    def destination = new File(parentDirectory, fileName)
                    if (!tempDir.listFiles().last().renameTo(destination))
                        throw new GradleException("Failed to write file to ${destination}")

                } else {

                    // flatten single top-level directory to behave more like docker.
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

                    // if directory already exists, rename each file into
                    // given directory, otherwise rename entire directory
                    if (hostPath.exists()) {
                        def parentName = tempDir.name
                        tempDir.listFiles().each {
                            def originPath = it.absolutePath
                            def index = originPath.lastIndexOf(parentName) + parentName.length()
                            def relativePath = originPath.substring(index, originPath.length())
                            def destFile = new File("${hostPath.absolutePath}/${relativePath}")
                            if (!it.renameTo(destFile))
                                throw new GradleException("Failed to write file to ${destFile}")
                        }
                    } else {
                        if (!tempDir.renameTo(hostPath))
                            throw new GradleException("Failed to write file to ${hostPath}")
                    }
                }
            }
        } finally {
            input?.close()
            tempFile?.delete()
            tempDir?.delete()
        }
    }
}
