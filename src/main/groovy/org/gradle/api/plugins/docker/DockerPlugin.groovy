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
import org.gradle.api.plugins.docker.tasks.AbstractDockerTask

class DockerPlugin implements Plugin<Project> {
    static final String DOCKER_JAVA_CONFIGURATION_NAME = 'dockerJava'
    static final String DOCKER_JAVA_DEFAULT_VERSION = '0.8.1'
    static final String EXTENSION_NAME = 'docker'

    @Override
    void apply(Project project) {
        project.configurations.create(DOCKER_JAVA_CONFIGURATION_NAME)
               .setVisible(false)
               .setTransitive(true)
               .setDescription('The Docker Java libraries to be used for this project.')

        def extension = project.extensions.create(EXTENSION_NAME, DockerExtension)

        configureAbstractDockerTask(project, extension)
    }

    private void configureAbstractDockerTask(Project project, extension) {
        project.tasks.withType(AbstractDockerTask) {
            conventionMapping.map('classpath') {
                def config = project.configurations[DOCKER_JAVA_CONFIGURATION_NAME]

                if(config.dependencies.empty) {
                    project.dependencies {
                        dockerJava "com.kpelykh:docker-java:$DOCKER_JAVA_DEFAULT_VERSION"
                    }
                }

                config
            }

            conventionMapping.map('serverUrl') { extension.serverUrl }
        }
    }
}