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
}
