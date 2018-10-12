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

import com.bmuschko.gradle.docker.utils.ThreadContextClassLoader
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

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
    final DirectoryProperty certPath = project.layout.directoryProperty()

    /**
     * The Docker remote API version.
     */
    @Input
    @Optional
    final Property<String> apiVersion = project.objects.property(String)

    protected ThreadContextClassLoader threadContextClassLoader

    private Action<? super Throwable> errorHandler
    protected Action<? super Object> nextHandler
    private Runnable completeHandler

    @TaskAction
    void start() {
        boolean commandFailed = false
        try {
            runInDockerClassPath { dockerClient ->
                runRemoteCommand(dockerClient)
            }
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
    void onNext(Action<? super Object> action) {
        nextHandler = action
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

    @CompileStatic(TypeCheckingMode.SKIP)
    private void runInDockerClassPath(Closure closure) {
        threadContextClassLoader.withContext(createDockerClientConfig(), closure)
    }

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = url.getOrNull()
        dockerClientConfig.certPath = certPath.getOrNull()
        dockerClientConfig.apiVersion = apiVersion.getOrNull()
        dockerClientConfig
    }

    abstract void runRemoteCommand(dockerClient)
}
