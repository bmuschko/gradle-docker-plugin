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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Copies the container logs into standard out/err, the same as the `docker logs` command. The container output
 * to standard out will go to standard out, and standard err to standard err.
 */
class DockerLogsContainer extends DockerExistingContainer {
    /**
     * Set to true to follow the output, which will cause this task to block until the container exists.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    Boolean follow

    /**
     * Set to true to copy all output since the container has started. For long running containers or containers
     * with a lot of output this could take a long time. This cannot be set if #tailCount is also set. Setting to false
     * leaves the decision of how many lines to copy to docker.
     * Default is unspecified (docker defaults to true).
     */
    @Input
    @Optional
    Boolean tailAll

    /**
     * Limit the number of lines of existing output. This cannot be set if #tailAll is also set.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    Integer tailCount

    /**
     * Include standard out.
     * Default is true.
     */
    @Input
    @Optional
    boolean stdOut = true


    /**
     * Include standard err.
     * Default is true.
     */
    @Input
    @Optional
    boolean stdErr = true

    /**
     * Set to the true to include a timestamp for each line in the output.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    Boolean showTimestamps

    /**
     * Limit the output to lines on or after the specified date.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    Date since

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Logs for container with ID '${getContainerId()}'."
        def logCommand = dockerClient.logContainerCmd(getContainerId())
        setContainerCommandConfig(logCommand)
        logCommand.exec(threadContextClassLoader.createLoggingCallback(logger))?.awaitCompletion()
    }

    private void setContainerCommandConfig(logsCommand) {
        if (getFollow() != null) {
            logsCommand.withFollowStream(getFollow())
        }

        if (getShowTimestamps() != null) {
            logsCommand.withTimestamps(getShowTimestamps())
        }

        logsCommand.withStdOut(getStdOut()).withStdErr(getStdErr())

        if (getTailAll() != null && getTailCount() != null) {
            throw new InvalidUserDataException("Conflicting parameters: only one of tailAll and tailCount can be specified")
        }

        if (getTailAll() != null) {
            logsCommand.withTailAll()
        } else if (getTailCount() != null) {
            logsCommand.withTail(getTailCount())
        }

        if (getSince()) {
            logsCommand.withSince((int) (getSince().time / 1000))
        }
    }
}

