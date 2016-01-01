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

    @Input
    String resource

    @Input
    @Optional
    File hostPath = project.projectDir

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

            /*
                1.) Tar stream is handed back which can contain N number of files. Because
                of this, we will explode into temp directory first, as we don't know
                how many files are to receive.
            */
            input = containerCommand.exec()
            tempFile = File.createTempFile(UUID.randomUUID().toString(), ".tar")
            tempFile.withOutputStream { it << input }


            // 2.) if compressed leave file as is otherwise untar
            if (compressed) {

                println "in compressed..."
                println "temp file at ${tempFile.absolutePath}"

                // ensure file ends with '.tar' if we are responsible for naming it
                def fileName = new File(resource).name
                def compressedFileName = (hostPath.exists() && hostPath.isDirectory()) ?
                        (fileName.endsWith(".tar") ? fileName : fileName + ".tar") :
                        hostPath.name

                def compressedFileLocation = (hostPath.exists() && hostPath.isDirectory()) ?
                        hostPath :
                        hostPath.parentFile

                println "compressedName ${compressedFileName}"
                println "compressedLocation ${compressedFileLocation.absolutePath}"


                if (hostPath.exists()) {
                    if (!hostPath.isDirectory()) { hostPath.delete() }
                } else {
                    hostPath.parentFile.mkdirs()
                }

                project.copy {
                    into compressedFileLocation
                    from tempFile
                    rename { compressedFileName }
                }
            } else {

                // copy raw contents of tar into tempDir
                tempDir = File.createTempDir()
                project.copy {
                    into tempDir
                    from project.tarTree(tempFile)
                }

                /*
                    3.) At this point we have 3 possibilities:

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
                    if (hostPath.exists() && !hostPath.isDirectory()) {
                        hostPath.delete()
                    }

                    // create parent files of hostPath should they not exist
                    if (!hostPath.exists()) {
                        hostPath.parentFile.mkdirs()
                    }

                    if (hostPath.isDirectory()) {
                        project.copy {
                            into hostPath
                            from tempDir.listFiles().last()
                        }
                    } else {
                        project.copy {
                            into hostPath.parentFile
                            from tempDir.listFiles().last()
                            rename { hostPath.name }
                        }
                    }
                } else {

                    // delete regular file should it exist
                    if (hostPath.exists() && !hostPath.isDirectory()) {
                        hostPath.delete()
                    }
                    if (!hostPath.exists()) {
                        hostPath.mkdirs()
                    }
                    project.copy {
                        into hostPath
                        from tempDir
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
