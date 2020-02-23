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

import com.bmuschko.gradle.docker.domain.LivenessProbe
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
import com.github.dockerjava.api.command.InspectContainerResponse
import org.gradle.api.GradleException
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.internal.logging.progress.ProgressLogger

import java.util.concurrent.TimeUnit

import static com.bmuschko.gradle.docker.internal.IOUtils.getProgressLogger

/**
 *  Poll a given running container for an arbitrary log message to confirm liveness. If a livenessProbe is
 *  NOT defined then we fallback to check if the container is in a running state.
 */
class DockerLivenessContainer extends DockerLogsContainer {

    @Nested
    @Optional
    LivenessProbe livenessProbe

    private Date internalSince

    // last call to inspect container which will only
    // be non-null once task has completed execution.
    private InspectContainerResponse lastInspection

    DockerLivenessContainer() {
        tailCount.finalizeValue()
        tailAll.finalizeValue()
        follow.finalizeValue()
    }

    @Override
    protected Date getInternalSince() {
        return internalSince
    }

    @Override
    void runRemoteCommand() {
        logger.quiet "Starting liveness probe on container with ID '${containerId.get()}'."

        // if livenessProbe was defined proceed as expected otherwise just
        // check if the container is up and running
        if (livenessProbe) {
            // create progressLogger for pretty printing of terminal log progression
            ProgressLogger progressLogger = getProgressLogger(project, DockerLivenessContainer)
            progressLogger.started()

            boolean matchFound = false

            try {
                long localPollTime = livenessProbe.pollTime
                int pollTimes = 0

                // 1.) Write the content of the logs into a StringWriter which we zero-out
                //     below after each successive log grab.
                setSink(new StringWriter())

                Date lastDate = since.getOrNull()

                while (localPollTime > 0) {
                    pollTimes += 1

                    // 2.) check if container is actually running
                    lastInspection = inspectContainer()

                    if (lastDate) {
                        this.internalSince = lastDate
                    }
                    final Date justBeforeLastExecutionDate = Calendar.getInstance().getTime()

                    // 3.) execute our "special" version of `runRemoteCommand` to
                    //     check if next log line has the message we're interested in
                    //     which in turn will have its output written into the sink.
                    _runRemoteCommand(dockerClient)

                    lastDate = justBeforeLastExecutionDate

                    // 4.) check if log contains expected message otherwise sleep
                    String logLine = getSink().toString()
                    if (logLine && logLine.contains(livenessProbe.logContains)) {
                        matchFound = true
                        break
                    } else {
                        long totalMillis = pollTimes * livenessProbe.pollInterval
                        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)
                        progressLogger.progress("Probing for ${totalMinutes}m...")
                        try {
                            // zero'ing out the below so as to save on memory for potentially
                            // big logs returned from container.
                            logLine = null
                            getSink().getBuffer().setLength(0)

                            localPollTime -= livenessProbe.pollInterval
                            sleep(livenessProbe.pollInterval)
                        } catch (Exception e) {
                            throw e
                        }
                    }
                }
            } finally {
                progressLogger.completed()
            }

            if (!matchFound) {
                throw new GradleException("Liveness probe failed to find a match: ${livenessProbe.toString()}")
            }
        } else {
            lastInspection = inspectContainer()
        }
    }

    private InspectContainerResponse inspectContainer() {
        InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerId.get()).exec()

        if (!inspectContainerResponse.getState().getRunning()) {
            throw new GradleException("Container with ID '${containerId.get()}' is not running.");
        }

        inspectContainerResponse
    }

    /**
     * Define the livenessProbe options for this liveness check. We'll default to
     * probing for 10 minutes with 30 second intervals between each livenessProbe.
     *
     * @param logContains content within container log we will search for
     */
    void livenessProbe(String logContains) {
        livenessProbe(600000, 30000, logContains)
    }

    /**
     * Define the livenessProbe options for this liveness check.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @param logContains content within container log we will search for
     */
    void livenessProbe(long pollTime, long pollInterval, String logContains) {
        this.livenessProbe = new LivenessProbe(pollTime, pollInterval, logContains)
    }

    // return the last inspection made during the lifetime of this task.
    def lastInspection() {
        lastInspection
    }
}
