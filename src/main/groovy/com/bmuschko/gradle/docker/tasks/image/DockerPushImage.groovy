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
import com.bmuschko.gradle.docker.internal.RegistryAuthLocator
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.github.dockerjava.api.command.PushImageCmd
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.core.command.PushImageResultCallback
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input

@CompileStatic
class DockerPushImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * The images including repository, image name and tag to be pushed e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    final SetProperty<String> images = project.objects.setProperty(String).empty()

    /**
     * {@inheritDoc}
     */
    final DockerRegistryCredentials registryCredentials

    DockerPushImage() {
        registryCredentials = project.objects.newInstance(DockerRegistryCredentials, project.objects)
    }

    @Override
    void runRemoteCommand() {
        if (images.get().empty) {
            throw new GradleException('No images configured for push operation.')
        }

        for (String image : images.get()) {
            AuthConfig authConfig = getRegistryAuthLocator().lookupAuthConfig(image, registryCredentials)
            logger.quiet "Pushing image '${image}' to ${RegistryAuthLocator.getRegistry(image)}."

            PushImageCmd pushImageCmd = dockerClient.pushImageCmd(image)
            pushImageCmd.withAuthConfig(authConfig)
            PushImageResultCallback callback = createCallback(nextHandler)
            pushImageCmd.exec(callback).awaitCompletion()
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials)
    }

    private PushImageResultCallback createCallback(Action nextHandler) {
        new PushImageResultCallback() {
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
    }
}
