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

import com.bmuschko.gradle.docker.internal.RegistryAuthLocator;
import com.bmuschko.gradle.docker.internal.services.DockerClientService;
import com.github.dockerjava.api.DockerClient;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class AbstractDockerRemoteApiTask extends DefaultTask {

    /**
     * Docker remote API server URL. Defaults to "http://localhost:2375".
     */
    @Input
    @Optional
    public final Property<String> getUrl() {
        return url;
    }

    private final Property<String> url = getProject().getObjects().property(String.class);

    /**
     * Path to the <a href="https://docs.docker.com/engine/security/https/">Docker certificate and key</a>.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public final DirectoryProperty getCertPath() {
        return certPath;
    }

    private final DirectoryProperty certPath = getProject().getObjects().directoryProperty();

    /**
     * The Docker remote API version.
     */
    @Input
    @Optional
    public final Property<String> getApiVersion() {
        return apiVersion;
    }

    private final Property<String> apiVersion = getProject().getObjects().property(String.class);

    @Internal
    public final Property<DockerClientService> getDockerClientService() {
        return dockerClientService;
    }

    private final Property<DockerClientService> dockerClientService = getProject().getObjects().property(DockerClientService.class);

    private Action<? super Throwable> errorHandler;
    private Action nextHandler;
    private Runnable completeHandler;

    @TaskAction
    public void start() throws Exception {
        boolean commandFailed = false;
        try {
            runRemoteCommand();
        } catch (Exception possibleException) {
            commandFailed = true;
            if (errorHandler != null) {
                errorHandler.execute(possibleException);
            } else {
                throw possibleException;
            }
        }

        if (!commandFailed && completeHandler != null) {
            completeHandler.run();
        }
    }

    /**
     * Reacts to a potential error occurring during the operation.
     *
     * @param action The action handling the error
     * @since 4.0.0
     */
    public void onError(Action<? super Throwable> action) {
        errorHandler = action;
    }

    /**
     * Reacts to data returned by an operation.
     *
     * @param action The action handling the data
     * @since 4.0.0
     */
    public void onNext(Action action) {
        nextHandler = action;
    }

    @Internal
    protected Action getNextHandler() {
        return nextHandler;
    }

    /**
     * Reacts to the completion of the operation.
     *
     * @param callback The callback to be executed
     * @since 4.0.0
     */
    public void onComplete(Runnable callback) {
        completeHandler = callback;
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
    public DockerClient getDockerClient() {
        return dockerClientService.get().getDockerClient(url, certPath, apiVersion);
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
        return factory.withDefaults();
    }

    private final RegistryAuthLocator.Factory factory = getProject().getObjects().newInstance(RegistryAuthLocator.Factory.class);

    public abstract void runRemoteCommand() throws Exception;
}
