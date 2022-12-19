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

import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.WaitResponse;
import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.concurrent.TimeUnit;

public class DockerWaitContainer extends DockerExistingContainer {

    private int exitCode;

    /**
     * Wait timeout in seconds.
     */
    @Input
    @Optional
    public final Property<Integer> getAwaitStatusTimeout() {
        return awaitStatusTimeout;
    }

    private final Property<Integer> awaitStatusTimeout = getProject().getObjects().property(Integer.class);

    @Override
    public void runRemoteCommand() {
        final String possibleTimeout = awaitStatusTimeout.getOrNull() != null ? " for " + getAwaitStatusTimeout().get() + " seconds" : "";
        getLogger().quiet("Waiting for container with ID '" + getContainerId().get() + "'" + possibleTimeout + ".");
        WaitContainerCmd containerCommand = getDockerClient().waitContainerCmd(getContainerId().get());
        WaitContainerResultCallback callback = containerCommand.exec(createCallback(getNextHandler()));
        exitCode = awaitStatusTimeout.getOrNull() != null ? callback.awaitStatusCode(awaitStatusTimeout.get(), TimeUnit.SECONDS) : callback.awaitStatusCode();
        getLogger().quiet("Container exited with code " + getExitCode());
    }

    @Internal
    public int getExitCode() {
        return exitCode;
    }

    private WaitContainerResultCallback createCallback(final Action nextHandler) {
        return new WaitContainerResultCallback() {
            @Override
            public void onNext(WaitResponse waitResponse) {
                if (nextHandler != null) {
                    try {
                        nextHandler.execute(waitResponse);
                    } catch (Exception e) {
                        getLogger().error("Failed to handle wait response", e);
                        return;
                    }
                }
                super.onNext(waitResponse);
            }

        };
    }
}
