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
package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.github.dockerjava.api.command.PullImageCmd
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.core.command.PullImageResultCallback
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

class DockerPullImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {
    /**
     * The tag to pull e.g. {@code my-java-app:1.2.3}. The tag should include the repository as needed.
     */
    @Input
    final Property<String> tag = project.objects.property(String)

    /**
     * {@inheritDoc}
     */
    DockerRegistryCredentials registryCredentials

    @Override
    void runRemoteCommand() {
        logger.quiet "Pulling image '${tag.get()}'."
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(tag.get())

        if(registryCredentials) {
            AuthConfig authConfig = new AuthConfig()
            authConfig.registryAddress = registryCredentials.url.get()

            if (registryCredentials.username.isPresent()) {
                authConfig.withUsername(registryCredentials.username.get())
            }

            if (registryCredentials.password.isPresent()) {
                authConfig.withPassword(registryCredentials.password.get())
            }

            if (registryCredentials.email.isPresent()) {
                authConfig.withEmail(registryCredentials.email.get())
            }

            pullImageCmd.withAuthConfig(authConfig)
        }

        PullImageResultCallback callback = new PullImageResultCallback() {
            @Override
            void onNext(PullResponseItem item) {
                if (nextHandler) {
                    try {
                        nextHandler.execute(item)
                    } catch (Exception e) {
                        logger.error('Failed to handle pull response', e)
                        return
                    }
                }
                super.onNext(item)
            }
        }

        pullImageCmd.exec(callback).awaitCompletion()
    }
}
