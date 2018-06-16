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

import com.bmuschko.gradle.docker.domain.ExecStopProbe
import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import java.util.concurrent.TimeUnit

import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

/**
 *  Run an "exec" command on a container with the intent that it will gracefully
 *  stop the container from within. We will then poll for a given amount of time
 *  to check if the container is stopped and if not then attempt to issue a stop
 *  command on the container.
 */
class DockerExecStopContainer extends DockerExecContainer {

    @Input
    @Optional
    ExecStopProbe probe

    /**
     * Stop timeout in seconds.
     */
    @Input
    @Optional
    Integer timeout

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Running exec-stop on container with ID '${getContainerId()}'."

        // 1.) we only need to proceed with an exec IF we have something to execute
        // otherwise treat this as a normal stop.
        if (this.getCmd()) {

            // 2.) kick the exec command
            _runRemoteCommand(dockerClient)

            // create progressLogger for pretty printing of terminal log progression.
            final def progressLogger = getProgressLogger(project, DockerExecStopContainer)
            progressLogger.started()

            // if no probe defined then create a default
            final ExecStopProbe localProbe = probe ?: new ExecStopProbe(600000, 30000)

            long localPollTime = localProbe.pollTime
            int pollTimes = 0
            boolean isRunning = true

            // 3.) poll for some amount of time until container is in a non-running state.
            while (isRunning && localPollTime > 0) {
                pollTimes += 1

                def container = dockerClient.inspectContainerCmd(getContainerId()).exec()
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
                logger.quiet "Container with ID '${getContainerId()}' did not gracefully shutdown: issuing stop."
                DockerStopContainer._runRemoteCommand(dockerClient, getContainerId(), timeout)
            }
        } else {
            DockerStopContainer._runRemoteCommand(dockerClient, getContainerId(), timeout)
        }
    }

    /**
     * Define the probe options for this exec/stop.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @return instance of ExecStopProbe
     */
    def probe(final long pollTime, final long pollInterval) {
        this.probe = new ExecStopProbe(pollTime, pollInterval)
    }
}
