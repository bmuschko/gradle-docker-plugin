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

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.response.ResponseHandler
import com.bmuschko.gradle.docker.response.image.BuildImageResponseHandler
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.bmuschko.gradle.docker.utils.TaskStateHelper
import org.gradle.api.tasks.*


class DockerBuildImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {
    private ResponseHandler<String, InputStream> responseHandler = new BuildImageResponseHandler()

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
    @Optional
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

    @Input
    @Optional
    Boolean pull

    /**
     * The target Docker registry credentials for building image.
     */
    @Nested
    @Optional
    DockerRegistryCredentials registryCredentials

    String imageId

    DockerBuildImage() {
        ext.getImageId = { imageId }

        doLast { saveImageId() }
        getOutputs().upToDateWhen { previouslyBuiltImageExists() }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Building image using context '${getInputDir()}'."
        def buildImageCmd

        if(getDockerFile()) {
            logger.quiet "Using Dockerfile '${getDockerFile()}'"
            buildImageCmd = dockerClient.buildImageCmd()
                    .withBaseDirectory(getInputDir())
                    .withDockerfile(getDockerFile())
        }
        else {
            buildImageCmd = dockerClient.buildImageCmd(getInputDir())
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

        if(getPull()) {
            buildImageCmd.withPull(getPull())
        }

        if(getRegistryCredentials()) {
            def authConfig = threadContextClassLoader.createAuthConfig(getRegistryCredentials())
            def authConfigurations = threadContextClassLoader.createAuthConfigurations([authConfig])
            buildImageCmd.withBuildAuthConfigs(authConfigurations)
        }

        InputStream response = buildImageCmd.exec()
        imageId = responseHandler.handle(response)
    }

    void setResponseHandler(ResponseHandler<String, InputStream> responseHandler) {
        this.responseHandler = responseHandler
    }

    void saveImageId() {
        def state = new TaskStateHelper(name, project)
        state.put("imageId", getImageId())
    }

    boolean previouslyBuiltImageExists() {
        def imageExists = false

        def imageId = new TaskStateHelper(name, project).get("imageId")
        if (!imageId?.trim()) {
            logger.info "No previously saved imageId exists"
            return false
        }

        runInDockerClassPath { dockerClient ->
            try {
                dockerClient.inspectImageCmd(imageId).exec()
                logger.info "Image ${imageId} found via call to inspectImage"
                imageExists = true
            } catch(Exception e) {
                logger.info "Image ${imageId} not found via call to inspectImage"
                imageExists = false
            }
        }
        imageExists
    }
}
