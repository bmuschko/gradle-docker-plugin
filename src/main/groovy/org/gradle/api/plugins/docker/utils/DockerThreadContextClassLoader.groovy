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
package org.gradle.api.plugins.docker.utils

import org.gradle.api.UncheckedIOException

import java.lang.reflect.Constructor

class DockerThreadContextClassLoader implements ThreadContextClassLoader {
    static final String DOCKER_CLIENT_CLASS = 'com.github.dockerjava.client.DockerClient'
    /**
     * {@inheritDoc}
     */
    @Override
    void withClasspath(Set<File> classpathFiles, String serverUrl, Closure closure) {
        ClassLoader originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = createClassLoader(classpathFiles)
            closure(getDockerClient(Thread.currentThread().contextClassLoader, serverUrl))
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
     * @param classLoader ClassLoader
     * @param serverUrl Docker server URL
     * @return DockerClient instance
     */
    private getDockerClient(ClassLoader classLoader, String serverUrl) {
        Class dockerClientClass = classLoader.loadClass(DOCKER_CLIENT_CLASS)
        Constructor constructor = dockerClientClass.getConstructor(String)
        constructor.newInstance(serverUrl)
    }
}
