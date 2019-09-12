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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerPushImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {
    /**
     * The image name e.g. "bmuschko/busybox" or just "busybox" if you want to default.
     */
    @Input
    final Property<String> imageName = project.objects.property(String)

    /**
     * The image names to push e.g. ["bmuschko/busybox"]
     */
    @Input
    final ListProperty<String> imageNames = project.objects.listProperty(String)

    /**
     * The image's tag.
     */
    @Input
    @Optional
    final Property<String> tag = project.objects.property(String)

    /**
     * {@inheritDoc}
     */
    DockerRegistryCredentials registryCredentials

    private List<String> getAllImageNames() {
        List<String> imageNames = imageNames.getOrElse([])
        if (imageName.getOrNull()) {
            imageNames.add(imageName.get())
        }
        return imageNames
    }

    @Override
    void runRemoteCommand() {
        for (imageName in getAllImageNames()) {
            PushImageCmd pushImageCmd = dockerClient.pushImageCmd(imageName)

            if (tag.getOrNull()) {
                pushImageCmd.withTag(tag.get())
                logger.quiet "Pushing image with name '${imageName}:${tag.get()}'."
            } else {
                logger.quiet "Pushing image with name '${imageName}'."
            }

            if (registryCredentials) {
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

            pushImageCmd.exec(callback).awaitSuccess()
        }
    }
}
