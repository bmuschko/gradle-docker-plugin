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
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Image;
import org.gradle.api.Action;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerListImages extends AbstractDockerRemoteApiTask {

    @Input
    @Optional
    public final Property<Boolean> getShowAll() {
        return showAll;
    }

    private final Property<Boolean> showAll = getProject().getObjects().property(Boolean.class);

    @Input
    @Optional
    public final Property<Boolean> getDangling() {
        return dangling;
    }

    private final Property<Boolean> dangling = getProject().getObjects().property(Boolean.class);

    @Input
    @Optional
    public final MapProperty<String, String> getLabels() {
        return labels;
    }

    private final MapProperty<String, String> labels = getProject().getObjects().mapProperty(String.class, String.class);

    @Input
    @Optional
    public final Property<String> getImageName() {
        return imageName;
    }

    public DockerListImages() {
        defaultResponseHandling();
    }

    private final Property<String> imageName = getProject().getObjects().property(String.class);

    @Override
    public void runRemoteCommand() {
        ListImagesCmd listImagesCmd = getDockerClient().listImagesCmd();

        if (Boolean.TRUE.equals(showAll.getOrNull())) {
            listImagesCmd.withShowAll(showAll.get());
        }

        if (Boolean.TRUE.equals(dangling.getOrNull())) {
            listImagesCmd.withDanglingFilter(dangling.get());
        }

        if (labels.getOrNull() != null && !labels.get().isEmpty()) {
            listImagesCmd.withLabelFilter(labels.get());
        }

        if (imageName.getOrNull() != null && !imageName.get().isEmpty()) {
            listImagesCmd.withImageNameFilter(imageName.get());
        }

        List<Image> images = listImagesCmd.exec();

        if (getNextHandler() != null) {
            for (Image image : images) {
                getNextHandler().execute(image);
            }
        }
    }

    private void defaultResponseHandling() {
        Action<Image> action = image -> {
            getLogger().quiet("Repository Tags : " + Arrays.stream(image.getRepoTags()).filter(Objects::nonNull).collect(Collectors.joining(", ")));
            getLogger().quiet("Image ID        : " + image.getId());
            getLogger().quiet("Created         : " + new Date(image.getCreated() * 1000));
            getLogger().quiet("Virtual Size    : " + image.getVirtualSize());
            getLogger().quiet("-----------------------------------------------");
        };

        onNext(action);
    }
}
