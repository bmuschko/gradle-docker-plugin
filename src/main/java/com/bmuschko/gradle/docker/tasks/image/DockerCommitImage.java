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

import com.bmuschko.gradle.docker.internal.RegularFileToStringTransformer;
import com.bmuschko.gradle.docker.tasks.container.DockerExistingContainer;
import com.github.dockerjava.api.command.CommitCmd;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class DockerCommitImage extends DockerExistingContainer {

    /**
     * The repository and image name e.g. {@code vieux/apache}.
     *
     * @since 9.0.0
     */
    @Input
    public final Property<String> getRepository() {
        return repository;
    }

    private final Property<String> repository = getProject().getObjects().property(String.class);

    /**
     * The tag e.g. {@code 2.0}.
     *
     * @since 9.0.0
     */
    @Input
    public final Property<String> getTag() {
        return tag;
    }

    private final Property<String> tag = getProject().getObjects().property(String.class);

    /**
     * Commit message.
     */
    @Input
    @Optional
    public final Property<String> getMessage() {
        return message;
    }

    private final Property<String> message = getProject().getObjects().property(String.class);

    /**
     * Author of image e.g. Benjamin Muschko.
     */
    @Input
    @Optional
    public final Property<String> getAuthor() {
        return author;
    }

    private final Property<String> author = getProject().getObjects().property(String.class);

    @Input
    @Optional
    public final Property<Boolean> getPause() {
        return pause;
    }

    private final Property<Boolean> pause = getProject().getObjects().property(Boolean.class);

    @Input
    @Optional
    public final Property<Boolean> getAttachStderr() {
        return attachStderr;
    }

    private final Property<Boolean> attachStderr = getProject().getObjects().property(Boolean.class);

    @Input
    @Optional
    public final Property<Boolean> getAttachStdin() {
        return attachStdin;
    }

    private final Property<Boolean> attachStdin = getProject().getObjects().property(Boolean.class);

    /**
     * Output file containing the image ID of the image committed.
     * Defaults to "$buildDir/.docker/$taskpath-imageId.txt".
     * If path contains ':' it will be replaced by '_'.
     */
    @OutputFile
    public final RegularFileProperty getImageIdFile() {
        return imageIdFile;
    }

    private final RegularFileProperty imageIdFile = getProject().getObjects().fileProperty();

    /**
     * The ID of the image committed. The value of this property requires the task action to be executed.
     */
    @Internal
    public final Property<String> getImageId() {
        return imageId;
    }

    private final Property<String> imageId = getProject().getObjects().property(String.class);

    public DockerCommitImage() {
        imageId.convention(imageIdFile.map(new RegularFileToStringTransformer()));

        final String safeTaskPath = getPath().replaceFirst("^:", "").replaceAll(":", "_");
        imageIdFile.convention(getProject().getLayout().getBuildDirectory().file(".docker/" + safeTaskPath + "-imageId.txt"));
    }

    @Override
    public void runRemoteCommand() throws IOException {
        getLogger().quiet("Committing image '" + getRepository().get() + ":" + getTag().get() + "' for container '" + getContainerId().get() + "'.");
        CommitCmd commitCmd = getDockerClient().commitCmd(getContainerId().get());
        commitCmd.withRepository(repository.get());
        commitCmd.withTag(tag.get());

        if (message.getOrNull() != null) {
            commitCmd.withMessage(message.get());
        }

        if (author.getOrNull() != null) {
            commitCmd.withAuthor(author.get());
        }

        if (Boolean.TRUE.equals(pause.getOrNull())) {
            commitCmd.withPause(pause.get());
        }

        if (Boolean.TRUE.equals(attachStderr.getOrNull())) {
            commitCmd.withAttachStderr(attachStderr.get());
        }

        if (Boolean.TRUE.equals(attachStdin.getOrNull())) {
            commitCmd.withAttachStdin(attachStdin.get());
        }

        String createdImageId = commitCmd.exec();
        Files.write(imageIdFile.get().getAsFile().toPath(), createdImageId.getBytes());
        getLogger().quiet("Created image with ID '" + createdImageId + "'.");
        if (getNextHandler() != null) {
            getNextHandler().execute(createdImageId);
        }
    }
}
