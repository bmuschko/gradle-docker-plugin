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

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

import java.util.concurrent.TimeUnit

class DockerWaitContainer extends DockerExistingContainer {

    private int exitCode

    /**
     * Wait timeout in seconds.
     */
    @Input
    @Optional
    final Property<Integer> timeout = project.objects.property(Integer)

    @Override
    void runRemoteCommand(dockerClient) {
        String possibleTimeout = timeout.getOrNull() ? " for ${timeout.get()} seconds" : ''
        logger.quiet "Waiting for container with ID '${containerId.get()}'${possibleTimeout}."
        def containerCommand = dockerClient.waitContainerCmd(containerId.get())
        def callback = threadContextClassLoader.createWaitContainerResultCallback(nextHandler)
        callback = containerCommand.exec(callback)
        exitCode = timeout.getOrNull() ? callback.awaitStatusCode(timeout.get(), TimeUnit.SECONDS) : callback.awaitStatusCode()
        logger.quiet "Container exited with code ${exitCode}"
    }

    @Internal
    int getExitCode() {
        exitCode
    }
}
