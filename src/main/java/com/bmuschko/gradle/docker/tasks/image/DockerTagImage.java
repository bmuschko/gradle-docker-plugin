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
package com.bmuschko.gradle.docker.tasks.image;

import com.github.dockerjava.api.command.TagImageCmd;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class DockerTagImage extends DockerExistingImage {
    /**
     * The repository to tag in.
     */
    @Input
    public final Property<String> getRepository() {
        return repository;
    }

    private final Property<String> repository = getProject().getObjects().property(String.class);

    /**
     * Image name to be tagged.
     */
    @Input
    public final Property<String> getTag() {
        return tag;
    }

    private final Property<String> tag = getProject().getObjects().property(String.class);

    /**
     * Forces tagging.
     */
    @Input
    @Optional
    public final Property<Boolean> getForce() {
        return force;
    }

    private final Property<Boolean> force = getProject().getObjects().property(Boolean.class);

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Tagging image with ID \'" + getImageId().get() + "\'.");
        TagImageCmd tagImageCmd = getDockerClient().tagImageCmd(getImageId().get(), repository.get(), tag.get());

        if (Boolean.TRUE.equals(force.getOrNull())) {
            tagImageCmd.withForce(force.get());
        }

        tagImageCmd.exec();
    }
}
