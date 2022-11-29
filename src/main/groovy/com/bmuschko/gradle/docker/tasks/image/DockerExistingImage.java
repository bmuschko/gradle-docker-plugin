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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;

import java.util.concurrent.Callable;

public abstract class DockerExistingImage extends AbstractDockerRemoteApiTask {
    /**
     * The ID or name of image used to perform operation. The image for the provided ID has to be created first.
     */
    @Input
    public final Property<String> getImageId() {
        return imageId;
    }

    private final Property<String> imageId = getProject().getObjects().property(String.class);

    private final ProviderFactory providers = getProject().getProviders();

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name
     * @see #targetImageId(Callable)
     * @see #targetImageId(Provider)
     */
    public void targetImageId(String imageId) {
        this.imageId.set(imageId);
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Callable
     * @see #targetImageId(String)
     * @see #targetImageId(Provider)
     */
    public void targetImageId(Callable<String> imageId) {
        targetImageId(providers.provider(imageId));
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Provider
     * @see #targetImageId(String)
     * @see #targetImageId(Callable)
     */
    public void targetImageId(Provider<String> imageId) {
        this.imageId.set(imageId);
    }
}
