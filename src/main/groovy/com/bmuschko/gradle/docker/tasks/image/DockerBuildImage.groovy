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
import com.bmuschko.gradle.docker.response.BuildImageResponseHandler
import com.bmuschko.gradle.docker.response.ResponseHandler
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional

class DockerBuildImage extends AbstractDockerRemoteApiTask {
    private final ResponseHandler<String> responseHandler = new BuildImageResponseHandler()

    /**
     * Input directory containing the build context. Defaults to "$projectDir/docker".
     */
    @InputDirectory
    File inputDir = project.file('docker')

    /**
     * The Dockerfile to use to build the image.  If null, will use 'Dockerfile' in the
     * build context, i.e. "$inputDir/Dockerfile".
     */
    @InputFile
    File dockerFile

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

    String imageId

    DockerBuildImage() {
        ext.getImageId = { imageId }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Building image using context '${getInputDir()}'."
        def buildImageCmd = dockerClient.buildImageCmd(getInputDir())

        if(getDockerFile()) {
            logger.quiet "Using Dockerfile '${getDockerFile()}'"
            buildImageCmd.withDockerfile(getDockerFile())
        }

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

        InputStream response = buildImageCmd.exec()
        imageId = responseHandler.handle(response)
    }
}