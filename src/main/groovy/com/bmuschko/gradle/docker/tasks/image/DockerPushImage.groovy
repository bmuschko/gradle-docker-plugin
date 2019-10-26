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
import com.github.dockerjava.api.command.PushImageCmd
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.core.command.PushImageResultCallback
import org.gradle.api.Action
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

class DockerPushImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * The images including repository, image name and tag used e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    final SetProperty<String> images = project.objects.setProperty(String).empty()

    /**
     * {@inheritDoc}
     */
    DockerRegistryCredentials registryCredentials

    DockerPushImage() {
        registryCredentials = project.objects.newInstance(DockerRegistryCredentials)
    }

    @Override
    void runRemoteCommand() {
        AuthConfig authConfig = createAuthConfig()

        images.get().each { image ->
            logger.quiet "Pushing image '${image}'."
            PushImageCmd pushImageCmd = dockerClient.pushImageCmd(image)

            if(authConfig) {
                pushImageCmd.withAuthConfig(authConfig)
            }

            PushImageResultCallback callback = new PushImageResultCallback() {
                @Override
                void onNext(PushResponseItem item) {
                    if (nextHandler) {
                        try {
                            nextHandler.execute(item)
                        } catch (Exception e) {
                            logger.error('Failed to handle push response', e)
                            return
                        }
                    }
                    super.onNext(item)
                }
            }

            pushImageCmd.exec(callback).awaitCompletion()
        }
    }

    private AuthConfig createAuthConfig() {
        AuthConfig authConfig = new AuthConfig()
        authConfig.withRegistryAddress(registryCredentials.url.get())

        if (registryCredentials.username.isPresent()) {
            authConfig.withUsername(registryCredentials.username.get())
        }

        if (registryCredentials.password.isPresent()) {
            authConfig.withPassword(registryCredentials.password.get())
        }

        if (registryCredentials.email.isPresent()) {
            authConfig.withEmail(registryCredentials.email.get())
        }

        authConfig
    }

    /**
     * Configures the target Docker registry credentials.
     *
     * @param action The action against the Docker registry credentials
     * @since 6.0.0
     */
    @Override
    void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials)
    }
}
