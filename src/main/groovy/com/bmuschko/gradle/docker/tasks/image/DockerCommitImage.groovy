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

import com.bmuschko.gradle.docker.tasks.container.DockerExistingContainer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCommitImage extends DockerExistingContainer {
    /**
     * Repository.
     */
    @Input
    @Optional
    String repository

    /**
     * Commit tag.
     */
    @Input
    @Optional
    String tag

    /**
     * Commit message.
     */
    @Input
    @Optional
    String message

    /**
     * Author of image e.g. Benjamin Muschko.
     */
    @Input
    @Optional
    String author

    @Input
    @Optional
    Boolean pause

    @Input
    @Optional
    Boolean attachStderr

    @Input
    @Optional
    Boolean attachStdin

    String imageId

    DockerCommitImage() {
        ext.getImageId = { imageId }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Commiting image for container '${getContainerId()}'."
        def commitCmd = dockerClient.commitCmd(getContainerId())

        if(getRepository()) {
            commitCmd.withRepository(getRepository())
        }

        if(getTag()) {
            commitCmd.withTag(getTag())
        }

        if(getMessage()) {
            commitCmd.withMessage(getMessage())
        }

        if(getAuthor()) {
            commitCmd.withAuthor(getAuthor())
        }

        if(getPause()) {
            commitCmd.withPause(getPause())
        }

        if(getAttachStderr()) {
            commitCmd.withAttachStderr(getAttachStderr())
        }

        if(getAttachStdin()) {
            commitCmd.withAttachStdin(getAttachStdin())
        }

        imageId = commitCmd.exec()
        logger.quiet "Created image with ID '$imageId'."
    }
}