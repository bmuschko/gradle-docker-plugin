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

import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import org.gradle.api.UncheckedIOException

import java.lang.reflect.Method

class DockerThreadContextClassLoader implements ThreadContextClassLoader {
    static final String DOCKER_CLIENT_CONFIG_CLASS = 'com.github.dockerjava.core.DockerClientConfig'
    static final String DOCKER_CLIENT_BUILDER_CLASS = 'com.github.dockerjava.core.DockerClientBuilder'

    /**
     * {@inheritDoc}
     */
    @Override
    void withClasspath(Set<File> classpathFiles, DockerClientConfiguration dockerClientConfiguration, Closure closure) {
        ClassLoader originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = createClassLoader(classpathFiles)
            closure(getDockerClient(dockerClientConfiguration))
        }
        finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    /**
     * Creates the classloader with the given classpath files.
     *
     * @param classpathFiles Classpath files
     * @return URL classloader
     */
    private URLClassLoader createClassLoader(Set<File> classpathFiles) {
        new URLClassLoader(toURLArray(classpathFiles), ClassLoader.systemClassLoader.parent)
    }

    /**
     * Creates URL array from a set of files.
     *
     * @param files Files
     * @return URL array
     */
    private URL[] toURLArray(Set<File> files) {
        List<URL> urls = new ArrayList<URL>(files.size())

        files.each { file ->
            try {
                urls << file.toURI().toURL()
            }
            catch(MalformedURLException e) {
                throw new UncheckedIOException(e)
            }
        }

        urls.toArray(new URL[urls.size()])
    }

    /**
     * Creates DockerClient from ClassLoader.
     *
     * @param dockerClientConfiguration Docker client configuration
     * @return DockerClient instance
     */
    private getDockerClient(DockerClientConfiguration dockerClientConfiguration) {
        // Create configuration
        Class dockerClientConfigClass = loadClass(DOCKER_CLIENT_CONFIG_CLASS)
        Method dockerClientConfigMethod = dockerClientConfigClass.getMethod('createDefaultConfigBuilder')
        def dockerClientConfigBuilder = dockerClientConfigMethod.invoke(null)
        dockerClientConfigBuilder.withUri(dockerClientConfiguration.url)

        if(dockerClientConfiguration.certPath) {
            dockerClientConfigBuilder.withDockerCertPath(dockerClientConfiguration.certPath.canonicalPath)
        }

        if(dockerClientConfiguration.registry) {
            dockerClientConfigBuilder.withServerAddress(dockerClientConfiguration.registry.url)
            dockerClientConfigBuilder.withUsername(dockerClientConfiguration.registry.username)
            dockerClientConfigBuilder.withPassword(dockerClientConfiguration.registry.password)
            dockerClientConfigBuilder.withEmail(dockerClientConfiguration.registry.email)
        }

        def dockerClientConfig = dockerClientConfigBuilder.build()

        // Create client
        Class dockerClientBuilderClass = loadClass(DOCKER_CLIENT_BUILDER_CLASS)
        Method method = dockerClientBuilderClass.getMethod('getInstance', dockerClientConfigClass)
        def dockerClientBuilder = method.invoke(null, dockerClientConfig)
        dockerClientBuilder.build()
    }

    @Override
    Class loadClass(String className) {
        Thread.currentThread().contextClassLoader.loadClass(className)
    }
}
