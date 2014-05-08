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
package org.gradle.api.plugins.docker.tasks

import org.apache.tools.ant.AntClassLoader
import org.gradle.api.DefaultTask
import org.gradle.api.UncheckedIOException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

import java.lang.reflect.Constructor

abstract class AbstractDockerTask extends DefaultTask {
    @InputFiles
    FileCollection classpath

    @Input
    String serverUrl = 'http://localhost:4243'
       
    @TaskAction
    void start() {
        withDockerJavaClassLoader { URLClassLoader classLoader ->
            runRemoteCommand(classLoader)
        }
    }

    private void withDockerJavaClassLoader(Closure c) {
        ClassLoader originalClassLoader = getClass().classLoader
        URLClassLoader dockerJavaClassloader = createCargoDaemonClassLoader()

        try {
            Thread.currentThread().contextClassLoader = dockerJavaClassloader
            c(dockerJavaClassloader)
        }
        finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    private URLClassLoader createCargoDaemonClassLoader() {
        ClassLoader rootClassLoader = new AntClassLoader(getClass().classLoader, false)
        new URLClassLoader(toURLArray(getClasspath().files), rootClassLoader)
    }

    private URL[] toURLArray(Collection<File> files) {
        List<URL> urls = new ArrayList<URL>(files.size())

        for(File file : files) {
            try {
                urls.add(file.toURI().toURL())
            }
            catch(MalformedURLException e) {
                throw new UncheckedIOException(e)
            }
        }

        urls.toArray(new URL[urls.size()])
    }


    protected getDockerClient(URLClassLoader classLoader) {
        Class dockerClientClass = classLoader.loadClass('com.kpelykh.docker.client.DockerClient')
        Constructor constructor = dockerClientClass.getConstructor(String)
        constructor.newInstance(getServerUrl())
    }

    abstract void runRemoteCommand(URLClassLoader classLoader)
}

