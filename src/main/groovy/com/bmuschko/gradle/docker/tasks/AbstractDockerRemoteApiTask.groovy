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

import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import groovy.transform.CompileStatic
import groovy.transform.Memoized
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

    private Action<? super Throwable> errorHandler
    protected Action<? super Object> nextHandler
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

    /**
     * Gets the Docker client uses to communicate with Docker via its remote API.
     * Initialized instance upon first request.
     * Returns the same instance for any successive method call.
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
    @Memoized
    DockerClient getDockerClient() {
        DockerClientConfiguration dockerClientConfiguration = createDockerClientConfig()
        DockerExtension dockerExtension = (DockerExtension) project.extensions.getByName(DockerRemoteApiPlugin.EXTENSION_NAME)
        String dockerUrl = getDockerHostUrl(dockerClientConfiguration, dockerExtension)
        File dockerCertPath = dockerClientConfiguration.certPath?.asFile ?: dockerExtension.certPath.getOrNull()?.asFile
        String apiVersion = dockerClientConfiguration.apiVersion ?: dockerExtension.apiVersion.getOrNull()

        // Create configuration
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder()
        dockerClientConfigBuilder.withDockerHost(dockerUrl)

        if (dockerCertPath) {
            dockerClientConfigBuilder.withDockerTlsVerify(true)
            dockerClientConfigBuilder.withDockerCertPath(dockerCertPath.canonicalPath)
        } else {
            dockerClientConfigBuilder.withDockerTlsVerify(false)
        }

        if (apiVersion) {
            dockerClientConfigBuilder.withApiVersion(apiVersion)
        }

        DefaultDockerClientConfig dockerClientConfig = dockerClientConfigBuilder.build()

        // Create client
        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build()

        // register buildFinished-hook to close kubernetes client.
        project.gradle.buildFinished {
            dockerClient.close()
        }
        dockerClient
    }

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = url.getOrNull()
        dockerClientConfig.certPath = certPath.getOrNull()
        dockerClientConfig.apiVersion = apiVersion.getOrNull()
        dockerClientConfig
    }

    /**
     * Checks if Docker host URL starts with http(s) and if so, converts it to tcp
     * which is accepted by docker-java library.
     *
     * @param dockerClientConfiguration docker client configuration
     * @return Docker host URL as string
     */
    private String getDockerHostUrl(DockerClientConfiguration dockerClientConfiguration, DockerExtension dockerExtension) {
        String url = (dockerClientConfiguration.url ?: dockerExtension.url.getOrNull()).toLowerCase()
        url.startsWith('http') ? 'tcp' + url.substring(url.indexOf(':')) : url
    }

    abstract void runRemoteCommand()
}
