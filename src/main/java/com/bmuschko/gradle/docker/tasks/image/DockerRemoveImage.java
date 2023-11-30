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

import com.github.dockerjava.api.command.RemoveImageCmd;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class DockerRemoveImage extends DockerExistingImage {

    /**
     * Configures `--force` option when removing the images.
     */
    @Input
    @Optional
    public final Property<Boolean> getForce() {
        return force;
    }

    /**
     * Configures `--no-prune` option when removing the images.
     */
    @Input
    @Optional
    public final Property<Boolean> getNoPrune() {
        return noPrune;
    }

    private final Property<Boolean> force = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> noPrune = getProject().getObjects().property(Boolean.class);

    private final ListProperty<String> images = getProject().getObjects().listProperty(String.class);

    /**
     * Sets the IDs or names of the images to be removed.
     *
     * @param images the names or IDs of the images.
     * @see #images(ListProperty)
     */
    public void images(List<String> images) {
        this.images.set(images);
    }

    /**
     * Sets the IDs or names of the images to be removed.
     *
     * @param images the names or IDs of the images.
     * @see #images(ListProperty)
     */
    public void images(String... images) {
        this.images.set(Arrays.asList(images));
    }

    /**
     * Sets the IDs or names of the images to be removed.
     *
     * @param images Image ID or name as {@link Callable}
     * @see #images(String...)
     * @see #images(ListProperty)
     */
    public void images(Callable<List<String>> images) {
        // TODO: How to do this, and is it necessary?
        // images(providers.provider(images));
    }

    /**
     * Sets the IDs or names of the images to be removed.
     *
     * @param images Image ID or name as {@link Provider}
     * @see #images(String...)
     */
    public void images(ListProperty<String> images) {
        this.images.set(images);
    }

    // Overriding methods to provide a warning message.
    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name
     * @see #targetImageId(Callable)
     * @see #targetImageId(Provider)
     */
    @Override
    public void targetImageId(String imageId) {
        logWarning();
        super.targetImageId(imageId);
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Callable
     * @see #targetImageId(String)
     * @see #targetImageId(Provider)
     */
    @Override
    public void targetImageId(Callable<String> imageId) {
        logWarning();
        super.targetImageId(imageId);
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Provider
     * @see #targetImageId(String)
     * @see #targetImageId(Callable)
     */
    @Override
    public void targetImageId(Provider<String> imageId) {
        logWarning();
        super.targetImageId(imageId);
    }

    private void logWarning() {
        getLogger().warn("Use property 'images' instead of 'targetImageId' when removing images");
    }

    @Override
    public void runRemoteCommand() {
        if (mixesBothProperties()) {
            throw new IllegalStateException("Project sets both properties 'images' and 'targetImageId', but only one is allowed.");
        }

        if (this.getImageId().isPresent()) {
            removeImage(this.getImageId().get());
        }

        if (this.images.isPresent()) {
            this.images.get().forEach(this::removeImage);
        }
    }

    private void removeImage(String imageId) {
        getLogger().quiet("Removing image with ID \'" + imageId + "\'.");
        try (RemoveImageCmd removeImageCmd = getDockerClient().removeImageCmd(imageId)) {
            if (Boolean.TRUE.equals(force.getOrNull())) {
                removeImageCmd.withForce(force.get());
            }

            if (Boolean.TRUE.equals(noPrune.getOrNull())) {
                removeImageCmd.withNoPrune(noPrune.get());
            }
            removeImageCmd.exec();
        }
    }

    private boolean mixesBothProperties() {
        return !this.images.get().isEmpty() && java.util.Optional.ofNullable(this.getImageId().getOrNull()).isPresent();
    }
}
