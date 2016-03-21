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
package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.logging.Logger

interface ThreadContextClassLoader {
    /**
     * Performs the closure with thread context classloader.
     *
     * @param classpathFiles Classpath files
     * @param dockerClientConfiguration Docker client configuration
     * @param closure the given closure
     */
    void withClasspath(Set<File> classpathFiles, DockerClientConfiguration dockerClientConfiguration, Closure closure)

    /**
     * Loads class with given name from thread context classloader.
     *
     * @param className Class name
     * @return Class
     */
    Class loadClass(String className)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/AuthConfig.java">AuthConfig</a>
     * from the thread context classloader.
     *
     * @param registryCredentials Registry credentials
     * @return Instance
     */
    def createAuthConfig(DockerRegistryCredentials registryCredentials)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/AuthConfigurations.java">AuthConfigurations</a>
     * from the thread context classloader.
     *
     * @param authConfigs Authentication configs
     * @return Instance
     */
    def createAuthConfigurations(List<Object> authConfigs)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Volume.java">Volume</a>
     * from thread context classloader.
     *
     * @param path Path to volume
     * @return Instance
     */
    def createVolume(String path)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Volumes.java">Volumes</a>
     * from thread context classloader.
     *
     * @param volumes List of Volumes
     * @return Instance
     */
    def createVolumes(List<Object> volumes)

    /**
     * Creates an array of instances of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/VolumesFrom.java">VolumesFrom</a>
     * from thread context classloader.
     *
     * @param volume Container name
     * @return Array of Instances
     */
    def createVolumesFrom(String[] volumes)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Link.java">Link</a>
     * from thread context classloader.
     *
     * @param link a container link
     * @return Instance
     */
    def createLink(String link)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Links.java">Links</a>
     * from thread context classloader.
     *
     * @param volumes List of Links
     * @return Instance
     */
    def createLinks(List<Object> links)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/HostConfig.java">Links</a>
     * from thread context classloader.
     *
     * @param hostConfigProperties a map containing all HostConfig properties to be set. The map entry key is the property name while the map entry value is the property value.
     * @return Instance
     */
    def createHostConfig(Map<String, String> hostConfigProperties)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/InternetProtocol.java">InternetProtocol</a>
     * from thread context classloader.
     *
     * @param scheme Scheme
     * @return Instance
     */
    def createInternetProtocol(String scheme)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/ExposedPort.java">ExposedPort</a>
     * from thread context classloader.
     *
     * @param scheme Scheme
     * @param port Port
     * @return Instance
     */
    def createExposedPort(String scheme, Integer port)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/ExposedPorts.java">ExposedPorts</a>
     * from thread context classloader.
     *
     * @param exposedPorts Exposed ports
     * @return Instance
     */
    def createExposedPorts(List<Object> exposedPorts)

    /**
     * Creates an array of instances of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/ExposedPort.java">ExposedPorts</a>
     * from thread context classloader.
     *
     * @param exposedPorts Exposed ports
     * @return An array of instances
     */
    def createExposedPortsArray(List<DockerCreateContainer.ExposedPort> exposedPorts)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/PortBinding.java">PortBinding</a>
     * from thread context classloader.
     *
     * @param portBinding Port binding
     * @return Instance
     */
    def createPortBinding(String portBinding)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Ports.java">Ports</a>
     * from thread context classloader.
     *
     * @param portBindings List of PortBindings
     * @return Instance
     */
    def createPorts(List<Object> portBindings)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Bind.java">Bind</a>
     * from thread context classloader.
     *
     * @param path The path on the local host to bind to the given volume.
     * @param volume The path to the volume of the container to bind to.
     * @return Instance
     */
    def createBind(String path, String volume)

    /**
     * Creates an array of instances of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/Bind.java">Bind</a>
     * from thread context classloader.
     *
     * @param binds A map of the binds to create. The keys are the path on the local host to bind
     * to the given volume. The values are the path to the volume of the container to bind to.
     * @return Array of Instance
     */
    def createBinds(Map<String, String> binds)

    /**
     * Creates instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/LogConfig.java">LogConfig</a>
     * from thread context classloader.
     *
     * @param type The type of log-driver to use (e.g. json-file, syslog, journald, none).
     * @param parameters Optional parameters for log-driver
     * @return Instance
     */
    def createLogConfig(String type, Map<String, String> parameters)

    /**
     * Creates the callback instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/command/BuildImageResultCallback.java">BuildImageResultCallback</a>
     * from thread context classloader.
     *
     * @param logger The logger instance which docker stream items will be printed to
     * @return Callback instance
     */
    def createBuildImageResultCallback(Logger logger)

    /**
     * Creates the callback instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/command/PushImageResultCallback.java">PushImageResultCallback</a>
     * from thread context classloader.
     *
     * @return Callback instance
     */
    def createPushImageResultCallback()

    /**
     * Creates the callback instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/command/PullImageResultCallback.java">PullImageResultCallback</a>
     * from thread context classloader.
     *
     * @return Callback instance
     */
    def createPullImageResultCallback()

    /**
     * Creates the callback instance of <a href="https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/command/LogContainerResultCallback.java">LogContainerResultCallback</a>
     * from thread context classloader. The callback is modified to send log lines to standard out and error.
     *
     * @return Callback instance
     */
    def createLoggingCallback(Logger logger)
}
