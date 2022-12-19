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
package com.bmuschko.gradle.docker.tasks.container;

import com.github.dockerjava.api.command.RemoveContainerCmd;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class DockerRemoveContainer extends DockerExistingContainer {

    @Input
    @Optional
    public final Property<Boolean> getRemoveVolumes() {
        return removeVolumes;
    }

    @Input
    @Optional
    public final Property<Boolean> getForce() {
        return force;
    }

    private final Property<Boolean> removeVolumes = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> force = getProject().getObjects().property(Boolean.class);

    @Override
    public void runRemoteCommand() {
        RemoveContainerCmd containerCommand = getDockerClient().removeContainerCmd(getContainerId().get());
        configureContainerCommandConfig(containerCommand);
        getLogger().quiet("Removing container with ID '" + getContainerId().get() + "'.");
        containerCommand.exec();
    }

    private void configureContainerCommandConfig(RemoveContainerCmd containerCommand) {
        if (Boolean.TRUE.equals(removeVolumes.getOrNull())) {
            containerCommand.withRemoveVolumes(removeVolumes.get());
        }

        if (Boolean.TRUE.equals(force.getOrNull())) {
            containerCommand.withForce(force.get());
        }
    }
}
