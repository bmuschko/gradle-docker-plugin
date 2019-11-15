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

import com.bmuschko.gradle.docker.domain.ExecProbe
import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.github.dockerjava.api.command.InspectContainerResponse
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

import java.util.concurrent.TimeUnit

import static com.bmuschko.gradle.docker.internal.IOUtils.getProgressLogger

/**
 *  Run an "exec" command on a container with the intent that it will gracefully
 *  stop the container from within. We will then poll for a given amount of time
 *  to check if the container is stopped and if not then attempt to issue a stop
 *  command on the container.
 */
@CompileStatic
class DockerExecStopContainer extends DockerExecContainer {

    @Nested
    @Optional
    ExecProbe execStopProbe

    /**
     * Stop timeout in seconds.
     */
    @Input
    @Optional
    final Property<Integer> awaitStatusTimeout = project.objects.property(Integer)

    @Override
    void runRemoteCommand() {
        logger.quiet "Running exec-stop on container with ID '${containerId.get()}'."

        // 1.) we only need to proceed with an exec IF we have something to execute
        // otherwise treat this as a normal stop.
        if (commands.get()) {

            // 2.) kick the exec command
            doRunRemoteCommand(dockerClient)

            // create progressLogger for pretty printing of terminal log progression.
            final def progressLogger = getProgressLogger(project, DockerExecStopContainer)
            progressLogger.started()

            // if no livenessProbe defined then create a default
            final ExecProbe localProbe = execStopProbe ?: new ExecProbe(600000, 30000)

            long localPollTime = localProbe.pollTime
            int pollTimes = 0
            boolean isRunning = true

            // 3.) poll for some amount of time until container is in a non-running state.
            while (isRunning && localPollTime > 0) {
                pollTimes += 1

                InspectContainerResponse container = dockerClient.inspectContainerCmd(containerId.get()).exec()
                isRunning = container.getState().getRunning()
                if (isRunning) {

                    long totalMillis = pollTimes * localProbe.pollInterval
                    long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)
                    progressLogger.progress("Waiting for ${totalMinutes}m...")
                    try {

                        localPollTime -= localProbe.pollInterval
                        sleep(localProbe.pollInterval)
                    } catch (Exception e) {
                        throw e
                    }
                } else {
                    break
                }
            }
            progressLogger.completed()

            // 4.) if container is still in a running state attempt to stop it
            //     the old fashioned way.
            if (isRunning) {
                logger.quiet "Container with ID '${containerId.get()}' did not gracefully shutdown: issuing stop."
                DockerStopContainer._runRemoteCommand(dockerClient, containerId.get(), awaitStatusTimeout.getOrNull())
            }
        } else {
            DockerStopContainer._runRemoteCommand(dockerClient, containerId.get(), awaitStatusTimeout.getOrNull())
        }
    }

    /**
     * Define the livenessProbe options for this exec/stop.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @return instance of ExecProbe
     */
    def execStopProbe(final long pollTime, final long pollInterval) {
        this.execStopProbe = new ExecProbe(pollTime, pollInterval)
    }
}
