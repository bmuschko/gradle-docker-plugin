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
import com.github.dockerjava.api.command.CommitCmd
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

class DockerCommitImage extends DockerExistingContainer {

    /**
     * The image including repository, image name and tag used e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    final Property<String> image = project.objects.property(String)

    /**
     * Commit message.
     */
    @Input
    @Optional
    final Property<String> message = project.objects.property(String)

    /**
     * Author of image e.g. Benjamin Muschko.
     */
    @Input
    @Optional
    final Property<String> author = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> pause = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> attachStderr = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> attachStdin = project.objects.property(Boolean)

    @Internal
    final Property<String> imageId = project.objects.property(String)

    @Override
    void runRemoteCommand() {
        logger.quiet "Committing image for container '${getContainerId().get()}'."
        CommitCmd commitCmd = dockerClient.commitCmd(getContainerId().get())
        commitCmd.withTag(image.get())

        if(message.getOrNull()) {
            commitCmd.withMessage(message.get())
        }

        if(author.getOrNull()) {
            commitCmd.withAuthor(author.get())
        }

        if(pause.getOrNull()) {
            commitCmd.withPause(pause.get())
        }

        if(attachStderr.getOrNull()) {
            commitCmd.withAttachStderr(attachStderr.get())
        }

        if(attachStdin.getOrNull()) {
            commitCmd.withAttachStdin(attachStdin.get())
        }

        String createdImageId = commitCmd.exec()
        imageId.set(createdImageId)
        logger.quiet "Created image with ID '$createdImageId'."
        if(nextHandler) {
            nextHandler.execute(createdImageId)
        }
    }
}
