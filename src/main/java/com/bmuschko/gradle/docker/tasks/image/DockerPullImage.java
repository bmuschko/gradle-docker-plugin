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
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class DockerPullImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * The image including repository, image name and tag to be pulled e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    public final Property<String> getImage() {
        return image;
    }

    private final Property<String> image = getProject().getObjects().property(String.class);

    /**
     * The target platform in the format {@code os[/arch[/variant]]}, for example {@code linux/s390x} or {@code darwin}.
     *
     * @since 7.1.0
     */
    @Input
    @Optional
    public final Property<String> getPlatform() {
        return platform;
    }

    private final Property<String> platform = getProject().getObjects().property(String.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public final DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    private final DockerRegistryCredentials registryCredentials;

    public DockerPullImage() {
        registryCredentials = getProject().getObjects().newInstance(DockerRegistryCredentials.class, getProject().getObjects());
    }

    @Override
    public void runRemoteCommand() throws InterruptedException {
        AuthConfig authConfig = getRegistryAuthLocator().lookupAuthConfig(image.get(), registryCredentials);
        getLogger().quiet("Pulling image '" + getImage().get() + "' from " + getRegistryAuthLocator().getRegistry(getImage().get()) + ".");

        PullImageCmd pullImageCmd = getDockerClient().pullImageCmd(image.get());

        if (platform.getOrNull() != null) {
            pullImageCmd.withPlatform(platform.get());
        }

        pullImageCmd.withAuthConfig(authConfig);
        PullImageResultCallback callback = createCallback(getNextHandler());
        pullImageCmd.exec(callback).awaitCompletion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials);
    }

    private PullImageResultCallback createCallback(final Action nextHandler) {
        return new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                if (nextHandler != null) {
                    try {
                        nextHandler.execute(item);
                    } catch (Exception e) {
                        getLogger().error("Failed to handle pull response", e);
                        return;
                    }
                }
                super.onNext(item);
            }
        };
    }
}
