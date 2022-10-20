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

import com.bmuschko.gradle.docker.services.DockerClientService
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceSpec

/**
 * Gradle plugin that provides custom tasks for interacting with Docker via its remote API.
 * <p>
 * Exposes the extension {@link DockerExtension) required to configure the communication and authentication with the Docker remote API. Provides Docker registry credential values from the extension to all custom tasks that implement {@link RegistryCredentialsAware}.
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
        configureRegistryCredentialsAwareTasks(project, dockerExtension.registryCredentials)

        Provider<DockerClientService> serviceProvider = project.getGradle().getSharedServices().registerIfAbsent("docker", DockerClientService.class, new Action<BuildServiceSpec<DockerClientService.Params>>() {
            @Override
            void execute(BuildServiceSpec<DockerClientService.Params> pBuildServiceSpec) {
                pBuildServiceSpec.parameters(parameters -> {
                    parameters.getUrl().set(dockerExtension.getUrl());
                    parameters.getCertPath().set(dockerExtension.getCertPath());
                    parameters.getApiVersion().set(dockerExtension.getApiVersion());
                })
            }
        })

        project.tasks.withType(AbstractDockerRemoteApiTask).configureEach(new Action<AbstractDockerRemoteApiTask>() {
            @Override
            void execute(AbstractDockerRemoteApiTask task) {
                task.dockerClientService.set(serviceProvider)
            }
        })
    }

    private void configureRegistryCredentialsAwareTasks(Project project, DockerRegistryCredentials extensionRegistryCredentials) {
        project.tasks.withType(RegistryCredentialsAware).configureEach(new Action<RegistryCredentialsAware>() {
            @Override
            void execute(RegistryCredentialsAware task) {
                task.registryCredentials.url.set(extensionRegistryCredentials.url)
                task.registryCredentials.username.set(extensionRegistryCredentials.username)
                task.registryCredentials.password.set(extensionRegistryCredentials.password)
                task.registryCredentials.email.set(extensionRegistryCredentials.email)
            }
        })
    }
}
