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

class DockerInspectImage extends DockerExistingImage {

    DockerInspectImage() {
        defaultResponseHandling()
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Inspecting image for with ID '${imageId.get()}'."
        def image = dockerClient.inspectImageCmd(imageId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(image)
        }
    }

    private void defaultResponseHandling() {
        Closure c = { image ->
            logger.quiet "ID               : $image.id"
            logger.quiet "Author           : $image.author"
            logger.quiet "Created          : $image.created"
            logger.quiet "Comment          : $image.comment"
            logger.quiet "Architecture     : $image.arch"
            logger.quiet "Operating System : $image.os"
            logger.quiet "Parent           : $image.parent"
            logger.quiet "Size             : $image.size"
            logger.quiet "Docker Version   : $image.dockerVersion"
            logger.quiet "Labels           : $image.config.labels"
        }

        nextHandler = c
    }
}
