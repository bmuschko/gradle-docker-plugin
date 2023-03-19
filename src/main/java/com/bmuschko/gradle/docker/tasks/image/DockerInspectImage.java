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

import com.github.dockerjava.api.command.InspectImageResponse;
import org.gradle.api.Action;

public class DockerInspectImage extends DockerExistingImage {

    public DockerInspectImage() {
        defaultResponseHandling();
    }

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Inspecting image with ID '" + getImageId().get() + "'.");
        InspectImageResponse image = getDockerClient().inspectImageCmd(getImageId().get()).exec();

        if (getNextHandler() != null) {
            getNextHandler().execute(image);
        }
    }

    private void defaultResponseHandling() {
        Action<InspectImageResponse> action = image -> {
            getLogger().quiet("ID               : " + image.getId());
            getLogger().quiet("Author           : " + image.getAuthor());
            getLogger().quiet("Created          : " + image.getCreated());
            getLogger().quiet("Comment          : " + image.getComment());
            getLogger().quiet("Architecture     : " + image.getArch());
            getLogger().quiet("Operating System : " + image.getOs());
            getLogger().quiet("Parent           : " + image.getParent());
            getLogger().quiet("Size             : " + image.getSize());
            getLogger().quiet("Docker Version   : " + image.getDockerVersion());
            getLogger().quiet("Labels           : " + image.getConfig().getLabels());
        };

        onNext(action);
    }
}
