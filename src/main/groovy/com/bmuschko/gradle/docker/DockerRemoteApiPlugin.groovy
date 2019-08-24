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

import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

/**
 * Gradle plugin that provides custom tasks for interacting with Docker via its remote API.
 * <p>
 * Exposes the extension {@link DockerExtension) required to configure the communication and authentication with the Docker remote API.
 */
@CompileStatic
class DockerRemoteApiPlugin implements Plugin<Project> {

    /**
     * The name of the extension.
     */
    public static final String EXTENSION_NAME = 'docker'

    /**
     * The group for all tasks created by this plugin.
     */
    public static final String DEFAULT_TASK_GROUP = 'Docker'

    @Override
    void apply(Project project) {
        DockerExtension dockerExtension = project.extensions.create(EXTENSION_NAME, DockerExtension, project.objects)
        DockerRegistryCredentials dockerRegistryCredentials = ((ExtensionAware) dockerExtension).extensions.create('registryCredentials', DockerRegistryCredentials, project.objects)
        configureRegistryAwareTasks(project, dockerRegistryCredentials)
    }

    private void configureRegistryAwareTasks(Project project, DockerRegistryCredentials dockerRegistryCredentials) {
        project.tasks.withType(RegistryCredentialsAware).configureEach( new Action<RegistryCredentialsAware>() {
            @Override
            void execute(RegistryCredentialsAware registryCredentialsAware) {
                registryCredentialsAware.setRegistryCredentials(dockerRegistryCredentials)
            }
        })
    }
}
