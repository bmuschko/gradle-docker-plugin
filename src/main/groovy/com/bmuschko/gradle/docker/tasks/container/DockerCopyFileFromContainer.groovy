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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import java.util.zip.GZIPOutputStream

class DockerCopyFileFromContainer extends DockerExistingContainer {

    /**
     * Path, either regular file or directory, inside container.
     */
    @Input
    String resource

    /**
     * Path, either regular file or directory, on host. If file does not exist
     * a regular file will be created with its name.
     *
     * If regular file then hostPath will be of type tgz. If directory then output
     * will be untarred into hostPath.
     */
    @Input
    @OutputFile
    File hostPath

    @Override
    void runRemoteCommand(dockerClient) {
        def containerCommand = dockerClient.copyFileFromContainerCmd(getContainerId(), getResource())
        logger.quiet "Copying '${getResource()}' from container with ID '${getContainerId()}' to '${getHostPath()}'."

        // create path (non-directory) if it does not already exist
        if (!hostPath.exists()) {
            hostPath.parentFile.mkdirs()
            hostPath.createNewFile()
        }

        def input = containerCommand.exec()
        def tempFile
        try {
            def workingFile = hostPath.isFile() ? hostPath : File.createTempFile(UUID.randomUUID().toString(), ".tgz")
            workingFile.withOutputStream { owner.compressFile(it, input) }
            if (hostPath.isDirectory()) {
                project.copy {
                    into hostPath
                    from project.tarTree(workingFile)
                }
            }
        } finally {
            input?.close()
            tempFile?.delete()
        }
    }

    protected compressFile(OutputStream outputStream, InputStream inputStream) {
        GZIPOutputStream gzipOut
        try {
            gzipOut = new GZIPOutputStream(outputStream)
            gzipOut << inputStream
        } finally {
            gzipOut?.close()
        }
    }
}
