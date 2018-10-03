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

import com.bmuschko.gradle.docker.domain.ExecProbe
import com.bmuschko.gradle.docker.tasks.ResultCallback
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

import java.util.concurrent.TimeUnit

import static com.bmuschko.gradle.docker.utils.IOUtils.getProgressLogger

class DockerExecContainer extends DockerExistingContainer implements ResultCallback {

    // set as optional for downstream sub-classes who configure
    // things a bit later.
    @Input
    @Optional
    @Deprecated // use the _commands_ param instead
    String[] cmd

    @Input
    @Optional
    final List<String[]> commands = []

    @Input
    @Optional
    Boolean attachStdout = true

    @Input
    @Optional
    Boolean attachStderr = true

    /**
     * Username or UID to execute the command as, with optional colon separated group or gid in format:
     * &lt;name|uid&gt;[:&lt;group|gid&gt;]
     *
     * @since 3.2.8
     */
    @Input
    @Optional
    String user

    // if set will check exit code of exec to ensure it's
    // within this list of allowed values otherwise through exception
    @Input
    @Optional
    List<Integer> successOnExitCodes

    // if `successOnExitCodes` are defined this livenessProbe will poll the "exec response"
    // until it is in a non-running state.
    @Nested
    @Optional
    ExecProbe execProbe

    @Internal
    @Deprecated // use the _execIds_ instead.
    private String execId

    @Internal
    private List<String> execIds = []

    DockerExecContainer() {
        ext.getExecId = { execId }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Executing on container with ID '${getContainerId()}'."
        _runRemoteCommand(dockerClient)
    }

    void _runRemoteCommand(dockerClient) {
        def execCallback = onNext ? threadContextClassLoader.createExecCallback(onNext) : threadContextClassLoader.createExecCallback(System.out, System.err)

        List<String[]> localCommands = commands()
        for (int i = 0; i < localCommands.size(); i++) {

            String [] singleCommand = localCommands.get(i)
            def execCmd = dockerClient.execCreateCmd(getContainerId())
            setContainerCommandConfig(execCmd, singleCommand)
            String localExecId = execCmd.exec().getId()
            dockerClient.execStartCmd(localExecId).withDetach(false).exec(execCallback).awaitCompletion()


            // create progressLogger for pretty printing of terminal log progression.
            final def progressLogger = getProgressLogger(project, DockerExecContainer)
            progressLogger.started()

            // if no livenessProbe defined then create a default
            final ExecProbe localProbe = execProbe ?: new ExecProbe(60000, 5000)

            long localPollTime = localProbe.pollTime
            int pollTimes = 0
            boolean isRunning = true

            // 3.) poll for some amount of time until container is in a non-running state.
            def lastExecResponse
            while (isRunning && localPollTime > 0) {
                pollTimes += 1

                lastExecResponse = DockerInspectExecContainer._runRemoteCommand(dockerClient, localExecId)
                isRunning = Boolean.valueOf(lastExecResponse.running)
                if (isRunning) {

                    long totalMillis = pollTimes * localProbe.pollInterval
                    long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)
                    progressLogger.progress("Executing for ${totalMinutes}m...")
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

            // if still running then throw an exception otherwise check the exitCode
            if (isRunning) {
                throw new GradleException("Exec '${singleCommand}' did not finish in a timely fashion: ${localProbe}")
            } else {
                if (successOnExitCodes) {
                    int exitCode = lastExecResponse.exitCode ?: 0
                    if (!successOnExitCodes.contains(exitCode)) {
                        throw new GradleException("${exitCode} is not a successful exit code. Valid values are ${successOnExitCodes}, response=${lastExecResponse}")
                    }
                }
            }

            execIds << localExecId
        }
    }

    // add multiple commands to be executed
    void withCommand(def commandToExecute) {
        if (commandToExecute) {
            commands << commandToExecute
        }
    }

    private void setContainerCommandConfig(containerCommand, String[] commandToExecute) {
        if (commandToExecute) {
            containerCommand.withCmd(commandToExecute)
        }

        if (getAttachStderr()) {
            containerCommand.withAttachStderr(getAttachStderr())
        }

        if (getAttachStdout()) {
            containerCommand.withAttachStdout(getAttachStdout())
        }

        if (getUser()) {
            containerCommand.withUser(getUser())
        }
    }

    protected List<String[]> commands() {
        final List<String[]> localCommands = new ArrayList<>(commands)
        if (cmd) {
            localCommands.add(cmd)
        }
        localCommands
    }

    def getExecId() {
        if (getExecIds()) {
            getExecIds().first()
        } else {
            null
        }
    }

    def getExecIds() {
        execIds
    }

    /**
     * Define the livenessProbe options for this exec.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @return instance of ExecProbe
     */
    def execProbe(final long pollTime, final long pollInterval) {
        this.execProbe = new ExecProbe(pollTime, pollInterval)
    }
}
