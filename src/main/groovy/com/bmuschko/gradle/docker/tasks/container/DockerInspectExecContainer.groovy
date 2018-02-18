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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

/**
 * Inspects task executed inside container
 * with {@link DockerExecContainer} command.
 */
class DockerInspectExecContainer extends AbstractDockerRemoteApiTask {

    /**
     * Exec ID used to perform operation. The exec for the provided
     * ID has to be created and started first.
     */
    @Input
    String execId

    void targetExecId(Closure execId) {
        conventionMapping.execId = execId
    }

    @Input
    String getExecId() {
        execId
    }

    @Override
    void runRemoteCommand(Object dockerClient) {
        logger.quiet "Inspecting exec with ID '${getExecId()}'."
        def result = dockerClient.inspectExecCmd(getExecId()).exec()
        if (onNext) {
            onNext.call(result)
        } else {
            logger.quiet("Exec ID: {}", result.id)
            logger.quiet("Container ID: {}", result.containerID)
            logger.quiet("Is running: {}", result.running)
            logger.quiet("Exit code: {}", result.exitCode)
        }
    }
}
