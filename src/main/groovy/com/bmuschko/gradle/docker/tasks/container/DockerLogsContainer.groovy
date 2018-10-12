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

import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.provider.Property
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
    final Property<Boolean> follow = project.objects.property(Boolean)

    /**
     * Set to true to copy all output since the container has started. For long running containers or containers
     * with a lot of output this could take a long time. This cannot be set if #tailCount is also set. Setting to false
     * leaves the decision of how many lines to copy to docker.
     * Default is unspecified (docker defaults to true).
     */
    @Input
    @Optional
    final Property<Boolean> tailAll = project.objects.property(Boolean)

    /**
     * Limit the number of lines of existing output. This cannot be set if #tailAll is also set.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    final Property<Integer> tailCount = project.objects.property(Integer)

    /**
     * Include standard out.
     * Default is true.
     */
    @Input
    @Optional
    final Property<Boolean> stdOut = project.objects.property(Boolean)


    /**
     * Include standard err.
     * Default is true.
     */
    @Input
    @Optional
    final Property<Boolean> stdErr = project.objects.property(Boolean)

    /**
     * Set to the true to include a timestamp for each line in the output.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    final Property<Boolean> showTimestamps = project.objects.property(Boolean)

    /**
     * Limit the output to lines on or after the specified date.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    final Property<Date> since = project.objects.property(Date)

    /**
     * Sink to write log output into
     */
    @Input
    @Optional
    Writer sink

    DockerLogsContainer() {
        stdOut.set(true)
        stdErr.set(true)
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Logs for container with ID '${containerId.get()}'."
        _runRemoteCommand(dockerClient)
    }

    // method used for sub-classes who wish to invoke this task
    // multiple times but don't want the logging message to be
    // printed for every iteration.
    void _runRemoteCommand(dockerClient) {
        def logCommand = dockerClient.logContainerCmd(containerId.get())
        setContainerCommandConfig(logCommand)
        logCommand.exec(createCallback())?.awaitCompletion()
    }

    private Object createCallback() {
        if(sink && nextHandler) {
            throw new GradleException("Define either sink or onNext")
        }
        if(sink) {
            return threadContextClassLoader.createLoggingCallback(sink)
        }
        if(nextHandler) {
            return threadContextClassLoader.createLoggingCallback(nextHandler)
        }

        threadContextClassLoader.createLoggingCallback()
    }

    private void setContainerCommandConfig(logsCommand) {
        if (follow.getOrNull() != null) {
            logsCommand.withFollowStream(follow.get())
        }

        if (showTimestamps.getOrNull() != null) {
            logsCommand.withTimestamps(showTimestamps.get())
        }

        logsCommand.withStdOut(stdOut.get()).withStdErr(stdErr.get())

        if (tailAll.getOrNull() && tailCount.getOrNull()) {
            throw new InvalidUserDataException("Conflicting parameters: only one of tailAll and tailCount can be specified")
        }

        if (tailAll.getOrNull()) {
            logsCommand.withTailAll()
        } else if (tailCount.getOrNull() != null) {
            logsCommand.withTail(tailCount.get())
        }

        if (since.getOrNull()) {
            logsCommand.withSince((int) (since.get().time / 1000))
        }
    }
}

