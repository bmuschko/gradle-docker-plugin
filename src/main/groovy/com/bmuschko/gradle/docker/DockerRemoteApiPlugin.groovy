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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.bmuschko.gradle.docker.utils.DockerThreadContextClassLoader
import com.bmuschko.gradle.docker.utils.ThreadContextClassLoader
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Gradle plugin that provides custom tasks for interacting with Docker via its remote API.
 */
class DockerRemoteApiPlugin implements Plugin<Project> {
    public static final String DOCKER_JAVA_CONFIGURATION_NAME = 'dockerJava'
    public static final String DOCKER_JAVA_DEFAULT_VERSION = '2.1.1'
    public static final String EXTENSION_NAME = 'docker'
    public static final String DEFAULT_TASK_GROUP = 'Docker'

    @Override
    void apply(Project project) {
        project.configurations.create(DOCKER_JAVA_CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription('The Docker Java libraries to be used for this project.')

        DockerExtension extension = project.extensions.create(EXTENSION_NAME, DockerExtension)

        configureAbstractDockerTask(project, extension)
        configureRegistryAwareTasks(project, extension)
    }

    private void configureAbstractDockerTask(Project project, DockerExtension extension) {
        ThreadContextClassLoader dockerClassLoader = new DockerThreadContextClassLoader()

        project.tasks.withType(AbstractDockerRemoteApiTask) {
            Configuration config = project.configurations[DOCKER_JAVA_CONFIGURATION_NAME]

            config.defaultDependencies { dependencies ->
                dependencies.add(project.dependencies.create("com.github.docker-java:docker-java:$DockerRemoteApiPlugin.DOCKER_JAVA_DEFAULT_VERSION"))
                dependencies.add(project.dependencies.create('org.slf4j:slf4j-simple:1.7.5'))
            }

            group = DEFAULT_TASK_GROUP
            threadContextClassLoader = dockerClassLoader

            conventionMapping.with {
                classpath = { config }
                url = { extension.url }
                certPath = { extension.certPath }
            }
        }
    }

    private void configureRegistryAwareTasks(Project project, DockerExtension extension) {
        project.tasks.withType(RegistryCredentialsAware) {
            conventionMapping.registryCredentials = { extension.registryCredentials }
        }
    }
}
