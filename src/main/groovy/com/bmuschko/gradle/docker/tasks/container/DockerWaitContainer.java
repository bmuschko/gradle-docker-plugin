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

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.WaitContainerCmd
import com.github.dockerjava.api.command.WaitContainerResultCallback
import com.github.dockerjava.api.model.WaitResponse
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

import java.util.concurrent.TimeUnit

@CompileStatic
class DockerWaitContainer extends DockerExistingContainer {

    private int exitCode

    /**
     * Wait timeout in seconds.
     */
    @Input
    @Optional
    final Property<Integer> awaitStatusTimeout = project.objects.property(Integer)

    @Override
    void runRemoteCommand() {
        String possibleTimeout = awaitStatusTimeout.getOrNull() ? " for ${awaitStatusTimeout.get()} seconds" : ''
        logger.quiet "Waiting for container with ID '${containerId.get()}'${possibleTimeout}."
        WaitContainerCmd containerCommand = dockerClient.waitContainerCmd(containerId.get())
        ResultCallback<WaitResponse> callback = containerCommand.exec(createCallback(nextHandler))
        exitCode = awaitStatusTimeout.getOrNull() ? callback.awaitStatusCode(awaitStatusTimeout.get(), TimeUnit.SECONDS) : callback.awaitStatusCode()
        logger.quiet "Container exited with code ${exitCode}"
    }

    @Internal
    int getExitCode() {
        exitCode
    }

    private WaitContainerResultCallback createCallback(Action nextHandler) {
        new WaitContainerResultCallback() {
            @Override
            void onNext(WaitResponse waitResponse) {
                if (nextHandler) {
                    try {
                        nextHandler.execute(waitResponse)
                    } catch (Exception e) {
                        logger.error('Failed to handle wait response', e)
                        return
                    }
                }
                super.onNext(waitResponse)
            }
        }
    }
}
