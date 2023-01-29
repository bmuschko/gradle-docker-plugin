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

import com.bmuschko.gradle.docker.domain.ExecProbe;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.logging.progress.ProgressLogger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.bmuschko.gradle.docker.internal.IOUtils.getProgressLogger;

public class DockerExecContainer extends DockerExistingContainer {

    @Input
    @Optional
    public final ListProperty<String[]> getCommands() {
        return commands;
    }

    @Input
    @Optional
    public final Property<Boolean> getAttachStdout() {
        return attachStdout;
    }

    @Input
    @Optional
    public final Property<Boolean> getAttachStderr() {
        return attachStderr;
    }

    /**
     * Username or UID to execute the command as, with optional colon separated group or gid in format:
     * &lt;name|uid&gt;[:&lt;group|gid&gt;]
     *
     * @since 3.2.8
     */
    @Input
    @Optional
    public final Property<String> getUser() {
        return user;
    }

    /**
     * Working directory in which the command is going to be executed.
     * Defaults to the WORKDIR set in docker file.
     *
     * @since 6.3.0
     */
    @Input
    @Optional
    public final Property<String> getWorkingDir() {
        return workingDir;
    }

    @Input
    @Optional
    public final ListProperty<Integer> getSuccessOnExitCodes() {
        return successOnExitCodes;
    }

    @Nested
    @Optional
    public ExecProbe getExecProbe() {
        return execProbe;
    }

    public void setExecProbe(ExecProbe execProbe) {
        this.execProbe = execProbe;
    }

    @Internal
    public final ListProperty<String> getExecIds() {
        return execIds;
    }

    private final ListProperty<String[]> commands = getProject().getObjects().listProperty(String[].class);
    private final Property<Boolean> attachStdout = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> attachStderr = getProject().getObjects().property(Boolean.class);
    private final Property<String> user = getProject().getObjects().property(String.class);
    private final Property<String> workingDir = getProject().getObjects().property(String.class);
    private final ListProperty<Integer> successOnExitCodes = getProject().getObjects().listProperty(Integer.class);
    private ExecProbe execProbe;
    private final ListProperty<String> execIds = getProject().getObjects().listProperty(String.class);

    public DockerExecContainer() {
        attachStdout.convention(true);
        attachStderr.convention(true);
    }

    @Override
    public void runRemoteCommand() throws InterruptedException {
        getLogger().quiet("Executing on container with ID '" + getContainerId().get() + "'.");
        doRunRemoteCommand(getDockerClient());
    }

    protected void doRunRemoteCommand(DockerClient dockerClient) throws InterruptedException {
        ResultCallback.Adapter<Frame> execCallback = createCallback(getNextHandler());

        List<String[]> localCommands = commands.get();
        for (int i = 0; i < commands.get().size(); i++) {

            final String[] singleCommand = localCommands.get(i);
            ExecCreateCmd execCmd = dockerClient.execCreateCmd(getContainerId().get());
            setContainerCommandConfig(execCmd, singleCommand);
            String localExecId = execCmd.exec().getId();
            dockerClient.execStartCmd(localExecId).withDetach(false).exec(execCallback).awaitCompletion();


            // create progressLogger for pretty printing of terminal log progression.
            final ProgressLogger progressLogger = getProgressLogger(getServices(), DockerExecContainer.class);
            progressLogger.started();

            // if no livenessProbe defined then create a default
            final ExecProbe localProbe = execProbe != null ? execProbe : new ExecProbe(60000, 2000);

            long localPollTime = localProbe.getPollTime();
            int pollTimes = 0;
            boolean isRunning = true;

            // 3.) poll for some amount of time until container is in a non-running state.
            InspectExecResponse lastExecResponse = null;
            while (isRunning && localPollTime > 0) {
                pollTimes += 1;

                lastExecResponse = dockerClient.inspectExecCmd(localExecId).exec();
                isRunning = lastExecResponse.isRunning();
                if (isRunning) {

                    long totalMillis = pollTimes * localProbe.getPollInterval();
                    final long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis);
                    progressLogger.progress("Executing for " + totalMinutes + "m...");
                    try {

                        localPollTime -= localProbe.getPollInterval();
                        Thread.sleep(localProbe.getPollInterval());
                    } catch (Exception e) {
                        throw e;
                    }
                } else {
                    break;
                }
            }
            progressLogger.completed();

            // if still running then throw an exception otherwise check the exitCode
            if (isRunning) {
                throw new GradleException("Exec '" + Arrays.toString(singleCommand) + "' did not finish in a timely fashion: " + localProbe);
            } else {
                if (successOnExitCodes.getOrNull() != null && !successOnExitCodes.get().isEmpty()) {
                    int exitCode = lastExecResponse.getExitCode() != null ? lastExecResponse.getExitCode().intValue() : 0;
                    if (!successOnExitCodes.get().contains(exitCode)) {
                        throw new GradleException(exitCode + " is not a successful exit code. Valid values are " + getSuccessOnExitCodes().get() + ", response=" + lastExecResponse);
                    }
                }
            }

            execIds.add(localExecId);
        }
    }

    /**
     * Convenience method for adding commands.
     *
     * @param commandsToExecute The commands to execute
     * @deprecated Use the method named {@link #getCommands()} directly
     */
    @Deprecated
    public void withCommand(List<String> commandsToExecute) {
        withCommand(commandsToExecute.toArray(String[]::new));
    }

    /**
     * Convenience method for adding commands.
     *
     * @param commandsToExecute The commands to execute
     * @deprecated Use the method named {@link #getCommands()} directly
     */
    @Deprecated
    public void withCommand(String[] commandsToExecute) {
        if (commandsToExecute != null) {
            commands.add(commandsToExecute);
        }
    }

    private void setContainerCommandConfig(ExecCreateCmd containerCommand, String[] commandToExecute) {
        if (commandToExecute != null) {
            containerCommand.withCmd(commandToExecute);
        }

        if (Boolean.TRUE.equals(attachStderr.getOrNull())) {
            containerCommand.withAttachStderr(attachStderr.get());
        }

        if (Boolean.TRUE.equals(attachStdout.getOrNull())) {
            containerCommand.withAttachStdout(attachStdout.get());
        }

        if (user.getOrNull() != null) {
            containerCommand.withUser(user.get());
        }


        if (workingDir.getOrNull() != null) {
            containerCommand.withWorkingDir(workingDir.get());
        }
    }

    /**
     * Define the livenessProbe options for this exec.
     *
     * @param pollTime how long we will poll for
     * @param pollInterval interval between poll requests
     * @return instance of ExecProbe
     */
    public ExecProbe execProbe(final long pollTime, final long pollInterval) {
        return this.execProbe = new ExecProbe(pollTime, pollInterval);
    }

    private ResultCallback.Adapter<Frame> createCallback(final Action nextHandler) {
        if (nextHandler != null) {
            return new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    try {
                        nextHandler.execute(frame);
                    } catch (Exception e) {
                        getLogger().error("Failed to handle frame", e);
                        return;
                    }
                    super.onNext(frame);
                }
            };
        }

        return new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame != null) {
                    switch (frame.getStreamType()) {
                        case STDOUT:
                        case RAW:
                            try {
                                System.out.write(frame.getPayload());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            System.out.flush();
                            break;
                        case STDERR:
                            try {
                                System.err.write(frame.getPayload());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            System.err.flush();
                            break;
                        default:
                            getLogger().error("unknown stream type:" + frame.getStreamType());
                    }
                }
            }
        };
    }
}
