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
package com.bmuschko.gradle.docker.tasks.container

import com.github.dockerjava.api.command.RemoveContainerCmd
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@CompileStatic
class DockerRemoveContainer extends DockerExistingContainer {

    @Input
    @Optional
    final Property<Boolean> removeVolumes = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> force = project.objects.property(Boolean)

    @Override
    void runRemoteCommand() {
        RemoveContainerCmd containerCommand = dockerClient.removeContainerCmd(containerId.get())
        configureContainerCommandConfig(containerCommand)
        logger.quiet "Removing container with ID '${containerId.get()}'."
        containerCommand.exec()
    }

    private void configureContainerCommandConfig(RemoveContainerCmd containerCommand) {
        if(removeVolumes.getOrNull()) {
            containerCommand.withRemoveVolumes(removeVolumes.get())
        }

        if(force.getOrNull()) {
            containerCommand.withForce(force.get())
        }
    }
}
