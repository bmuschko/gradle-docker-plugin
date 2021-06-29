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
import com.bmuschko.gradle.docker.internal.RegistryAuthLocator
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
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
import java.time.Duration

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

    /**
     * Determines the timeout until a new connection is fully established.
     *
     * <b>Only used if HTTP is used as the transport.</b>
     *
     * This may also include transport security negotiation exchanges
     * such as {@code SSL} or {@code TLS} protocol negotiation).
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    @Input
    @Optional
    final Property<Long> httpConnectionTimeout = project.objects.property(Long)

    /**
     * Determines the timeout until arrival of a response from the opposite
     * endpoint. <b>Only used if HTTP is used as the transport.</b>
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Please note that response timeout may be unsupported by
     * HTTP transports with message multiplexing.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    @Input
    @Optional
    final Property<Long> httpResponseTimeout = project.objects.property(Long)

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
        Long httpConnectionTimeout = dockerClientConfiguration.httpConnectionTimeout != null ? dockerClientConfiguration.httpConnectionTimeout : dockerExtension.httpConnectionTimeout.getOrNull()
        Long httpResponseTimeout = dockerClientConfiguration.httpResponseTimeout != null ? dockerClientConfiguration.httpResponseTimeout : dockerExtension.httpResponseTimeout.getOrNull()

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

        DockerClient dockerClient = createDefaultDockerClient(dockerClientConfig, httpConnectionTimeout, httpResponseTimeout)
        // register buildFinished-hook to close docker client.
        project.gradle.buildFinished {
            dockerClient.close()
        }
        dockerClient
    }

    private DockerClient createDefaultDockerClient(DockerClientConfig config, Long httpConnectionTimeout, Long httpResponseTimeout) {
        ApacheDockerHttpClient.Builder builder = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
        if (httpResponseTimeout != null) {
            builder.responseTimeout(httpResponseTimeout == 0L ? Duration.ZERO : Duration.ofMinutes(httpResponseTimeout))
        }
        if (httpConnectionTimeout != null) {
            builder.connectionTimeout(httpConnectionTimeout == 0L ? Duration.ZERO : Duration.ofMinutes(httpConnectionTimeout))
        }
        ApacheDockerHttpClient dockerClient = builder.build()
        DockerClientImpl.getInstance(
            config,
            dockerClient
        )
    }

    /**
     * Returns the instance of {@link RegistryAuthLocator}.
     * <p>
     * Unless other credentials information provided, the instance returns authConfig object provided by the Docker client.
     *
     * @return The registry authentication locator
     */
    @Internal
    @Memoized
    protected RegistryAuthLocator getRegistryAuthLocator() {
        new RegistryAuthLocator()
    }

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = url.getOrNull()
        dockerClientConfig.certPath = certPath.getOrNull()
        dockerClientConfig.apiVersion = apiVersion.getOrNull()
        dockerClientConfig.httpConnectionTimeout = httpConnectionTimeout.getOrNull()
        dockerClientConfig.httpResponseTimeout = httpResponseTimeout.getOrNull()
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
