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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.util.concurrent.Callable;

public abstract class DockerExistingContainer extends AbstractDockerRemoteApiTask {
    /**
     * The ID or name of container used to perform operation. The container for the provided ID has to be created first.
     */
    @Input
    public final Property<String> getContainerId() {
        return containerId;
    }

    /**
     * Sets the target container ID or name.
     *
     * @param containerId Container ID or name
     * @see #targetContainerId(Callable)
     * @see #targetContainerId(Provider)
     */
    public void targetContainerId(String containerId) {
        this.containerId.set(containerId);
    }

    /**
     * Sets the target container ID or name.
     *
     * @param containerId Container ID or name as Callable
     * @see #targetContainerId(String)
     * @see #targetContainerId(Provider)
     */
    public void targetContainerId(Callable<String> containerId) {
        targetContainerId(getProject().provider(containerId));
    }

    /**
     * Sets the target container ID or name.
     *
     * @param containerId Container ID or name as Provider
     * @see #targetContainerId(String)
     * @see #targetContainerId(Callable)
     */
    public void targetContainerId(Provider<String> containerId) {
        this.containerId.set(containerId);
    }

    private final Property<String> containerId = getProject().getObjects().property(String.class);
}
