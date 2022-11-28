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
package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.internal.RegistryAuthLocator
import com.bmuschko.gradle.docker.internal.services.DockerClientService
import com.github.dockerjava.api.DockerClient
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CompileStatic
abstract class AbstractDockerRemoteApiTask extends DefaultTask {

    /**
     * Docker remote API server URL. Defaults to "http://localhost:2375".
     */
    @Input
    @Optional
    final Property<String> url = project.objects.property(String)

    /**
     * Path to the <a href="https://docs.docker.com/engine/security/https/">Docker certificate and key</a>.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final DirectoryProperty certPath = project.objects.directoryProperty()

    /**
     * The Docker remote API version.
     */
    @Input
    @Optional
    final Property<String> apiVersion = project.objects.property(String)

    @Internal
    final Property<DockerClientService> dockerClientService = project.objects.property(DockerClientService)

    private Action<? super Throwable> errorHandler
    private Action nextHandler
    private Runnable completeHandler

    @TaskAction
    void start() {
        boolean commandFailed = false
        try {
            runRemoteCommand()
        } catch (Exception possibleException) {
            commandFailed = true
            if (errorHandler) {
                errorHandler.execute(possibleException)
            } else {
                throw possibleException
            }
        }
        if(!commandFailed && completeHandler) {
            completeHandler.run()
        }
    }

    /**
     * Reacts to a potential error occurring during the operation.
     *
     * @param action The action handling the error
     * @since 4.0.0
     */
    void onError(Action<? super Throwable> action) {
        errorHandler = action
    }

    /**
     * Reacts to data returned by an operation.
     *
     * @param action The action handling the data
     * @since 4.0.0
     */
    void onNext(Action action) {
        nextHandler = action
    }

    @Internal
    protected Action getNextHandler() {
        nextHandler
    }

    /**
     * Reacts to the completion of the operation.
     *
     * @param callback The callback to be executed
     * @since 4.0.0
     */
    void onComplete(Runnable callback) {
        completeHandler = callback
    }

    /**
     * Gets the Docker client uses to communicate with Docker via its remote API.
     * Initialized instance upon first request.
     * Returns the same instance for any successive method call.
     * To support the configuration cache we rely on DockerClientService's internal cache.
     * <p>
     * Before accessing the Docker client, all data used for configuring its runtime behavior needs to be evaluated.
     * The data includes:
     * <ol>
     * <li>The property values of this class</li>
     * <li>The plugin's extension property values</li>
     * </ol>
     * <p>
     * It is safe to access the Docker client under the following conditions:
     * <ol>
     * <li>In the task action</li>
     * <li>In the task's constructor if used in {@code Action} or {@code Closure} of {@code outputs.upToDateWhen}</li>
     * </ol>
     *
     * @return The Docker client
     */
    @Internal
    DockerClient getDockerClient() {
        dockerClientService.get().getDockerClient(createDockerClientConfig())
    }

    /**
     * Returns the instance of {@link RegistryAuthLocator}.
     * <p>
     * Unless other credentials information provided, the instance returns authConfig object provided by the Docker client.
     *
     * @return The registry authentication locator
     */
    @Internal
    protected RegistryAuthLocator getRegistryAuthLocator() {
        registryAuthLocator
    }

    private final RegistryAuthLocator registryAuthLocator = new RegistryAuthLocator()

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = url.getOrNull()
        dockerClientConfig.certPath = certPath.getOrNull()
        dockerClientConfig.apiVersion = apiVersion.getOrNull()
        dockerClientConfig
    }

    abstract void runRemoteCommand()
}
