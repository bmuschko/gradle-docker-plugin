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
package com.bmuschko.gradle.docker.tasks.container.extras

import com.bmuschko.gradle.docker.tasks.container.DockerExistingContainer
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerWaitHealthyContainer extends DockerExistingContainer {

    /**
     * Wait timeout in seconds.
     */
    @Input
    @Optional
    final Property<Integer> timeout = project.objects.property(Integer)

    /**
     * Interval between each check in milliseconds.
     */
    @Input
    @Optional
    final Property<Integer> checkInterval = project.objects.property(Integer)

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Waiting for container with ID '${containerId.get()}' to be healthy."

        def command = dockerClient.inspectContainerCmd(containerId.get())
        Long deadline = timeout.getOrNull() ? System.currentTimeMillis() + timeout.get() * 1000 : null
        long sleepInterval = checkInterval.getOrNull() ?: 500

        while(!check(deadline, command)) {
            sleep(sleepInterval)
        }
    }

    private boolean check(Long deadline, def command) {
        if (deadline && System.currentTimeMillis() > deadline) {
            throw new GradleException('Health check timeout expired')
        }

        def response = command.exec()
        def state = response.state
        if (!state.running) {
            throw new GradleException("Container with ID '${getContainerId()}' is not running")
        }

        String healthStatus
        if (state.health) {
            healthStatus = state.health.status
        } else {
            logger.quiet 'HEALTHCHECK instruction was not used to build this image. Falling back to generic Status of container...'
            healthStatus = state.status
        }

        if (nextHandler) {
            nextHandler.execute(healthStatus)
        }
        return healthStatus ==~ /(healthy|running)/
    }
}
