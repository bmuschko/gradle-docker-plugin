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
package org.gradle.api.plugins.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.docker.tasks.AbstractDockerTask

class DockerBasePlugin implements Plugin<Project> {
    static final String DOCKER_JAVA_CONFIGURATION_NAME = 'dockerJava'
    static final String DOCKER_JAVA_DEFAULT_VERSION = '0.9.1'
    static final String EXTENSION_NAME = 'docker'

    @Override
    void apply(Project project) {
        project.configurations.create(DOCKER_JAVA_CONFIGURATION_NAME)
               .setVisible(false)
               .setTransitive(true)
               .setDescription('The Docker Java libraries to be used for this project.')

        DockerExtension extension = project.extensions.create(EXTENSION_NAME, DockerExtension)

        configureAbstractDockerTask(project, extension)
    }

    private void configureAbstractDockerTask(Project project, DockerExtension extension) {
        project.tasks.withType(AbstractDockerTask) {
            def config = project.configurations[DOCKER_JAVA_CONFIGURATION_NAME]

            config.incoming.beforeResolve {
                if(config.dependencies.empty) {
                    Dependency dockerJavaDependency = project.dependencies.create("com.github.docker-java:docker-java:$DOCKER_JAVA_DEFAULT_VERSION")
                    config.dependencies.add(dockerJavaDependency)
                    Dependency log4jOverSlf4jDependency = project.dependencies.create('org.slf4j:slf4j-simple:1.7.5')
                    config.dependencies.add(log4jOverSlf4jDependency)
                }
            }

            conventionMapping.with {
                classpath = { config }
                serverUrl = { extension.serverUrl }
                username = { extension.credentials?.username }
                password = { extension.credentials?.password }
                email = { extension.credentials?.email }
            }
        }
    }
}