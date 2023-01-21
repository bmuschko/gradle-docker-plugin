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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Frame;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;

/**
 * Copies the container logs into standard out/err, the same as the `docker logs` command. The container output
 * to standard out will go to standard out, and standard err to standard err.
 */
public class DockerLogsContainer extends DockerExistingContainer {

    /**
     * Set to true to follow the output, which will cause this task to block until the container exists.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    public final Property<Boolean> getFollow() {
        return follow;
    }

    /**
     * Set to true to copy all output since the container has started. For long running containers or containers
     * with a lot of output this could take a long time. This cannot be set if #tailCount is also set. Setting to false
     * leaves the decision of how many lines to copy to docker.
     * Default is unspecified (docker defaults to true).
     */
    @Input
    @Optional
    public final Property<Boolean> getTailAll() {
        return tailAll;
    }

    /**
     * Limit the number of lines of existing output. This cannot be set if #tailAll is also set.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    public final Property<Integer> getTailCount() {
        return tailCount;
    }

    /**
     * Include standard out.
     * Default is true.
     */
    @Input
    @Optional
    public final Property<Boolean> getStdOut() {
        return stdOut;
    }

    /**
     * Include standard err.
     * Default is true.
     */
    @Input
    @Optional
    public final Property<Boolean> getStdErr() {
        return stdErr;
    }

    /**
     * Set to the true to include a timestamp for each line in the output.
     * Default is unspecified (docker defaults to false).
     */
    @Input
    @Optional
    public final Property<Boolean> getShowTimestamps() {
        return showTimestamps;
    }

    /**
     * Limit the output to lines on or after the specified date.
     * Default is unspecified (docker defaults to all lines).
     */
    @Input
    @Optional
    public final Property<Date> getSince() {
        return since;
    }

    /**
     * Sink to write log output into.
     */
    @OutputFile
    @Optional
    public RegularFileProperty getSink() {
        return sink;
    }

    @Internal
    protected Date getInternalSince() {
        return since.getOrNull();
    }

    private final Property<Boolean> follow = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> tailAll = getProject().getObjects().property(Boolean.class);
    private final Property<Integer> tailCount = getProject().getObjects().property(Integer.class);
    private final Property<Boolean> stdOut = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> stdErr = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> showTimestamps = getProject().getObjects().property(Boolean.class);
    private final Property<Date> since = getProject().getObjects().property(Date.class);
    private RegularFileProperty sink = getProject().getObjects().fileProperty();

    public DockerLogsContainer() {
        stdOut.convention(true);
        stdErr.convention(true);
    }

    @Override
    public void runRemoteCommand() throws InterruptedException {
        getLogger().quiet("Logs for container with ID '" + getContainerId().get() + "'.");
        logAndProcessResponse(getDockerClient());
    }

    // method used for sub-classes who wish to invoke this task
    // multiple times but don't want the logging message to be
    // printed for every iteration.
    public void logAndProcessResponse(DockerClient dockerClient) throws InterruptedException {
        LogContainerCmd logCommand = dockerClient.logContainerCmd(getContainerId().get());
        setContainerCommandConfig(logCommand);
        logCommand.exec(createCallback(getNextHandler())).awaitCompletion();
    }

    private ResultCallback.Adapter<Frame> createCallback(final Action nextHandler) {
        if (sink.isPresent() && nextHandler != null) {
            throw new GradleException("Define either sink or onNext");
        }
        if (sink.isPresent()) {
            return new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    switch (frame.getStreamType()) {
                        case STDOUT:
                        case RAW:
                        case STDERR:
                            try {
                                Files.write(sink.get().getAsFile().toPath(), frame.getPayload(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                            break;
                    }
                    super.onNext(frame);
                }

            };
        }

        if (nextHandler != null) {
            return new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    nextHandler.execute(frame);
                    super.onNext(frame);
                }

            };
        }

        return new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                switch (frame.getStreamType()) {
                    case STDOUT:
                    case RAW:
                        getLogger().quiet(new String(frame.getPayload()).replaceFirst( "/\\s+$/", ""));
                        break;
                    case STDERR:
                        getLogger().error(new String(frame.getPayload()).replaceFirst("/\\s+$/", ""));
                        break;
                }
                super.onNext(frame);
            }

        };
    }

    private void setContainerCommandConfig(LogContainerCmd logsCommand) {
        if (follow.getOrNull() != null) {
            logsCommand.withFollowStream(follow.get());
        }

        if (showTimestamps.getOrNull() != null) {
            logsCommand.withTimestamps(showTimestamps.get());
        }

        logsCommand.withStdOut(stdOut.get()).withStdErr(stdErr.get());

        if (Boolean.TRUE.equals(tailAll.getOrNull()) && tailCount.getOrNull() != null) {
            throw new InvalidUserDataException("Conflicting parameters: only one of tailAll and tailCount can be specified");
        }


        if (Boolean.TRUE.equals(tailAll.getOrNull())) {
            logsCommand.withTailAll();
        } else if (tailCount.getOrNull() != null) {
            logsCommand.withTail(tailCount.get());
        }

        Date since = getInternalSince();
        if (since != null) {
            logsCommand.withSince((int) (since.getTime() / 1000));
        }
    }
}
