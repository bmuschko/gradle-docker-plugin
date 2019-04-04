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
            DockerExtension dockerExtension = (DockerExtension) project.extensions.getByName(DockerRemoteApiPlugin.EXTENSION_NAME)
            runRemoteCommand(getDockerClient(createDockerClientConfig(), dockerExtension))
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

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = url.getOrNull()
        dockerClientConfig.certPath = certPath.getOrNull()
        dockerClientConfig.apiVersion = apiVersion.getOrNull()
        dockerClientConfig
    }

    /**
     * Get, and possibly create, DockerClient.
     *
     * @param dockerClientConfiguration Docker client configuration
     * @param classpathFiles set of files containing DockerClient jars
     * @return DockerClient instance
     */
    @Memoized
    private DockerClient getDockerClient(DockerClientConfiguration dockerClientConfiguration, DockerExtension dockerExtension) {
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

        // register shutdown-hook to close kubernetes client.
        addShutdownHook {
            dockerClient.close()
        }
        dockerClient
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

    abstract void runRemoteCommand(DockerClient dockerClient)
}
