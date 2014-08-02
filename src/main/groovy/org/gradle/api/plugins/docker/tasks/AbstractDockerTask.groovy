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
package org.gradle.api.plugins.docker.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.docker.utils.DockerThreadContextClassLoader
import org.gradle.api.plugins.docker.utils.ThreadContextClassLoader
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

abstract class AbstractDockerTask extends DefaultTask {
    /**
     * Classpath for Docker Java libraries.
     */
    @InputFiles
    FileCollection classpath

    /**
     * Docker remote API server URL. Defaults to "http://localhost:2375".
     */
    @Input
    String serverUrl = 'http://localhost:2375'

    /**
     * Repository username needed to push containers. Defaults to null.
     */
    @Input
    @Optional
    String username

    /**
     * Repository password needed to push containers. Defaults to null.
     */
    @Input
    @Optional
    String password

    /**
     * Repository email address needed to push containers. Defaults to null.
     */
    @Input
    @Optional
    String email

    ThreadContextClassLoader threadContextClassLoader = new DockerThreadContextClassLoader()

    @TaskAction
    void start() {
        threadContextClassLoader.withClasspath(getClasspath().files, getServerUrl()) { dockerClient ->
            if(getUsername() && getPassword() && getEmail()) {
                dockerClient.setCredentials(getUsername(), getPassword(), getEmail())
            }

            runRemoteCommand(dockerClient)
        }
    }

    abstract void runRemoteCommand(dockerClient)
}

