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
package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.utils.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The image repository.
     */
    @Input
    String repository

    /**
     * The image's tag.
     */
    @Input
    @Optional
    String tag

    /**
     * Where to save image. Can't be null.
     */
    @OutputFile
    File destFile

    @Override
    void runRemoteCommand(Object dockerClient) {
        if (!destFile || !destFile.isFile()) {
            throw new GradleException("Invalid destination file: " + destFile)
        }
        def saveImageCmd = dockerClient.saveImageCmd(getRepository())

        if (getTag()) {
            saveImageCmd.withTag(getTag())
        }
        InputStream image = saveImageCmd.exec()
        FileOutputStream fs = new FileOutputStream(destFile)
        try {
            IOUtils.copy(image, fs)
        } catch (IOException e) {
            throw new GradleException("Can't save image.", e)
        } finally {
            IOUtils.closeQuietly(image)
        }
    }
}
