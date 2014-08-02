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
package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.plugins.docker.tasks.AbstractDockerTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerListImages extends AbstractDockerTask {
    @Input
    @Optional
    Boolean showAll

    @Input
    @Optional
    String filter

    @Override
    void runRemoteCommand(dockerClient) {
        def listImagesCmd = dockerClient.listImagesCmd()

        if(getShowAll()) {
            listImagesCmd.withShowAll(getShowAll())
        }

        if(getFilter()) {
            listImagesCmd.withFilter(getFilter())
        }

        def images = listImagesCmd.exec()

        images.each { image ->
            logger.quiet "Repository Tags : ${image.repoTags?.join(', ')}"
            logger.quiet "Image ID        : $image.id"
            logger.quiet "Created         : ${new Date(image.created * 1000)}"
            logger.quiet "Virtual Size    : $image.virtualSize"
            logger.quiet "-----------------------------------------------"
        }
    }
}
