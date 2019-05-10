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

import com.github.dockerjava.api.command.RemoveImageCmd
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerRemoveImage extends DockerExistingImage {

    @Input
    @Optional
    final Property<Boolean> force = project.objects.property(Boolean)

    @Override
    void runRemoteCommand() {
        logger.quiet "Removing image with ID '${imageId.get()}'."
        RemoveImageCmd removeImageCmd = dockerClient.removeImageCmd(imageId.get())

        if (force.getOrNull()) {
            removeImageCmd.withForce(force.get())
        }

        removeImageCmd.exec()
    }
}
