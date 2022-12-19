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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StopContainerCmd;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class DockerStopContainer extends DockerExistingContainer {

    /**
     * Stop timeout in seconds.
     */
    @Input
    @Optional
    public final Property<Integer> getWaitTime() {
        return waitTime;
    }

    private final Property<Integer> waitTime = getProject().getObjects().property(Integer.class);

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Stopping container with ID '" + getContainerId().get() + "'.");
        _runRemoteCommand(getDockerClient(), getContainerId().get(), waitTime.getOrNull());
    }

    public static void _runRemoteCommand(DockerClient dockerClient, String containerId, Integer optionalTimeout) {
        StopContainerCmd stopContainerCmd = dockerClient.stopContainerCmd(containerId);
        if (optionalTimeout != null) {
            stopContainerCmd.withTimeout(optionalTimeout);
        }

        stopContainerCmd.exec();
    }
}
