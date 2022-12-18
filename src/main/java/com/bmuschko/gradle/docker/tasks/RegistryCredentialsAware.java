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
package com.bmuschko.gradle.docker.tasks;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.Nested;

public interface RegistryCredentialsAware extends Task {
    /**
     * The target Docker registry credentials for usage with a task.
     */
    @Nested
    DockerRegistryCredentials getRegistryCredentials();

    /**
     * Configures the target Docker registry credentials for use with a task.
     *
     * @param action The action against the Docker registry credentials
     * @since 6.0.0
     */
    void registryCredentials(Action<? super DockerRegistryCredentials> action);
}
