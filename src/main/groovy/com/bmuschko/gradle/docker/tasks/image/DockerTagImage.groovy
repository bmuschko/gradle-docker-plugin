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

import com.github.dockerjava.api.command.TagImageCmd
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerTagImage extends DockerExistingImage {
    /**
     * The repository to tag in.
     */
    @Input
    final Property<String> repository = project.objects.property(String)

    /**
     * Image name to be tagged.
     */
    @Input
    final Property<String> tag = project.objects.property(String)

    /**
     * Forces tagging.
     */
    @Input
    @Optional
    final Property<Boolean> force = project.objects.property(Boolean)

    @Override
    void runRemoteCommand() {
        logger.quiet "Tagging image with ID '${imageId.get()}'."
        TagImageCmd tagImageCmd = dockerClient.tagImageCmd(imageId.get(), repository.get(), tag.get())

        if(force.getOrNull()) {
            tagImageCmd.withForce(force.get())
        }

        tagImageCmd.exec()
    }
}
