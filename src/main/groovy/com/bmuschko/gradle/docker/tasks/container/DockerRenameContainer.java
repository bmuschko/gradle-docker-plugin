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
package com.bmuschko.gradle.docker.tasks.container;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public class DockerRenameContainer extends DockerExistingContainer {
    @Input
    public final Property<String> getRenameTo() {
        return renameTo;
    }

    private final Property<String> renameTo = getProject().getObjects().property(String.class);

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Renaming container with ID '" + getContainerId().get() + "' to '" + getRenameTo().get() + "'.");
        getDockerClient().renameContainerCmd(getContainerId().get()).withName(renameTo.get()).exec();
    }
}
