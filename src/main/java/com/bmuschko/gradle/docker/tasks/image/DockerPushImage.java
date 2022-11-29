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
package com.bmuschko.gradle.docker.tasks.image;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.PushResponseItem;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;

public class DockerPushImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * The images including repository, image name and tag to be pushed e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    public final SetProperty<String> getImages() {
        return images;
    }

    private final SetProperty<String> images = getProject().getObjects().setProperty(String.class);

    /**
     * {@inheritDoc}
     */
    public final DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    private final DockerRegistryCredentials registryCredentials;

    public DockerPushImage() {
        registryCredentials = getProject().getObjects().newInstance(DockerRegistryCredentials.class, getProject().getObjects());
    }

    @Override
    public void runRemoteCommand() throws Exception {
        if (images.get().isEmpty()) {
            throw new GradleException("No images configured for push operation.");
        }

        for (String image : images.get()) {
            AuthConfig authConfig = getRegistryAuthLocator().lookupAuthConfig(image, registryCredentials);
            getLogger().quiet("Pushing image '" + image + "' to " + getRegistryAuthLocator().getRegistry(image) + ".");

            PushImageCmd pushImageCmd = getDockerClient().pushImageCmd(image);
            pushImageCmd.withAuthConfig(authConfig);
            ResultCallback.Adapter<PushResponseItem> callback = createCallback(getNextHandler());
            pushImageCmd.exec(callback).awaitCompletion();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials);
    }

    private ResultCallback.Adapter<PushResponseItem> createCallback(final Action nextHandler) {
        return new ResultCallback.Adapter<PushResponseItem>() {
            @Override
            public void onNext(PushResponseItem item) {
                if (nextHandler != null) {
                    nextHandler.execute(item);
                }
            }

        };
    }
}
