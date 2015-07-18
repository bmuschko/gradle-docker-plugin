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
import com.bmuschko.gradle.docker.response.image.BuildImageResponseHandler
import com.bmuschko.gradle.docker.response.ResponseHandler
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

class DockerBuildImage extends AbstractDockerRemoteApiTask {
    private ResponseHandler<String, InputStream> responseHandler = new BuildImageResponseHandler()

    /**
     * Input directory containing Dockerfile. Defaults to "$projectDir/docker".
     */
    @InputDirectory
    File inputDir = project.file('docker')

    /**
     * Tag for image.
     */
    @Input
    @Optional
    String tag

    @Input
    @Optional
    Boolean noCache

    @Input
    @Optional
    Boolean remove

    @Input
    @Optional
    Boolean quiet

    @Input
    @Optional
    Boolean pull

    String imageId

    DockerBuildImage() {
        ext.getImageId = { imageId }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Building image from folder '${getInputDir()}'."
        def buildImageCmd = dockerClient.buildImageCmd(getInputDir())

        if(getTag()) {
            logger.quiet "Using tag '${getTag()}' for image."
            buildImageCmd.withTag(getTag())
        }

        if(getNoCache()) {
            buildImageCmd.withNoCache(getNoCache())
        }

        if(getRemove()) {
            buildImageCmd.withRemove(getRemove())
        }

        if(getQuiet()) {
            buildImageCmd.withQuiet(getQuiet())
        }

        if(getPull()) {
            buildImageCmd.withPull(getPull())
        }

        InputStream response = buildImageCmd.exec()
        imageId = responseHandler.handle(response)
    }

    void setResponseHandler(ResponseHandler<String, InputStream> responseHandler) {
        this.responseHandler = responseHandler
    }
}