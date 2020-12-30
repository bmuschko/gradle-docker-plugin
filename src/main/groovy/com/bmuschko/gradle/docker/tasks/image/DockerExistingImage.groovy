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

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskProvider

import java.util.concurrent.Callable

@CompileStatic
abstract class DockerExistingImage extends AbstractDockerRemoteApiTask {
    /**
     * The ID or name of image used to perform operation. The image for the provided ID has to be created first.
     */
    @Input
    final Property<String> imageId = project.objects.property(String)

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name
     * @see #targetImageId(Callable)
     * @see #targetImageId(Provider)
     * @see #targetImageId(TaskProvider)
     */
    void targetImageId(String imageId) {
        this.imageId.set(imageId)
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Callable
     * @see #targetImageId(String)
     * @see #targetImageId(Provider)
     * @see #targetImageId(TaskProvider)
     */
    void targetImageId(Callable<String> imageId) {
        targetImageId(project.provider(imageId))
    }

    /**
     * Sets the target image ID or name.
     *
     * @param imageId Image ID or name as Provider
     * @see #targetImageId(String)
     * @see #targetImageId(Callable)
     * @see #targetImageId(TaskProvider)
     */
    void targetImageId(Provider<String> imageId) {
        this.imageId.set(imageId)
    }

    /**
     * Sets the target image ID or name lazily as derived from the given build task.
     *
     * The image ID will be used if available, otherwise the first image name.
     *
     * @param buildImageTask a DockerBuildImage task as a TaskProvider
     * @see #targetImageId(String)
     * @see #targetImageId(Callable)
     * @see #targetImageId(Provider)
     */
    void targetImageId(TaskProvider<DockerBuildImage> buildImageTask) {
        targetImageId(buildImageTask.flatMap { DockerBuildImage buildTask ->
            project.provider({
                buildTask.imageId.getOrNull() ?: buildTask.images.get().first()
            } as Callable<String>)
        })
    }
}
