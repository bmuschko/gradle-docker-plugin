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
package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * The extension for configuring the Docker communication via the remote API through the {@link DockerRemoteApiPlugin}.
 * <p>
 * Other convention plugins like the {@link DockerJavaApplicationPlugin} and {@link DockerSpringBootApplicationPlugin} may further extend this extension as nested configuration elements.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     url = 'https://192.168.59.103:2376'
 * }
 * </pre>
 */
@CompileStatic
class DockerExtension {

    private final Logger logger = Logging.getLogger(DockerExtension)

    /**
     * The server URL to connect to via Docker’s remote API.
     * <p>
     * Defaults to {@code unix:///var/run/docker.sock} for Unix systems and {@code tcp://127.0.0.1:2375} for Windows systems.
     */
    final Property<String> url

    /**
     * The path to certificates for communicating with Docker over SSL.
     * <p>
     * Defaults to value of environment variable {@code DOCKER_CERT_PATH} if set.
     */
    final DirectoryProperty certPath

    /**
     * The remote API version. For most cases this can be left null.
     */
    final Property<String> apiVersion

    /**
     * The target Docker registry credentials.
     */
    final DockerRegistryCredentials registryCredentials

    DockerExtension(ObjectFactory objectFactory) {
        url = objectFactory.property(String)
        url.set(getDefaultDockerUrl())
        certPath = objectFactory.directoryProperty()

        File defaultDockerCert = getDefaultDockerCert()

        if (defaultDockerCert) {
            certPath.set(defaultDockerCert)
        }

        apiVersion = objectFactory.property(String)
        registryCredentials = objectFactory.newInstance(DockerRegistryCredentials, objectFactory)
    }

    /**
     * Configures the target Docker registry credentials.
     *
     * @param action The action against the Docker registry credentials
     * @since 6.0.0
     */
    void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials)
    }

    private String getDefaultDockerUrl() {
        String dockerUrl = System.getenv("DOCKER_HOST")
        if (!dockerUrl) {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win")
            if (!isWindows && new File('/var/run/docker.sock').exists()) {
                dockerUrl = 'unix:///var/run/docker.sock'
            } else {
                if (isWindows && new File("\\\\.\\pipe\\docker_engine").exists()) {
                    // TODO: re-enable once docker-java supports named pipes. Relevant links:
                    //
                    //     https://github.com/bmuschko/gradle-docker-plugin/pull/313
                    //     https://github.com/docker-java/docker-java/issues/765
                    //
                    // dockerUrl = 'npipe:////./pipe/docker_engine'
                    dockerUrl = 'tcp://127.0.0.1:2375'
                } else {
                    dockerUrl = 'tcp://127.0.0.1:2375'
                }
            }
        }
        logger.info("Default docker.url set to $dockerUrl")
        dockerUrl
    }

    private File getDefaultDockerCert() {
        String dockerCertPath = System.getenv("DOCKER_CERT_PATH")
        if(dockerCertPath) {
            File certFile = new File(dockerCertPath)
            if(certFile.exists()) {
                return certFile
            }
        }
        return null
    }
}
