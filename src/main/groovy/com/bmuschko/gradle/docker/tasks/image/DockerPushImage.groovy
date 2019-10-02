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

import com.bmuschko.gradle.docker.tasks.AbstractCredentialsAwareRemoteApiTask
import com.github.dockerjava.api.command.PushImageCmd
import com.github.dockerjava.api.model.PushResponseItem
import com.github.dockerjava.core.command.PushImageResultCallback
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerPushImage extends AbstractCredentialsAwareRemoteApiTask {
    /**
     * The image name e.g. "bmuschko/busybox" or just "busybox" if you want to default.
     */
    @Input
    final Property<String> imageName = project.objects.property(String)

    /**
     * The image's tag.
     */
    @Input
    @Optional
    final Property<String> tag = project.objects.property(String)

    @Override
    void runRemoteCommand() {
        PushImageCmd pushImageCmd = dockerClient
            .pushImageCmd(imageName.get())

        if(tag.getOrNull()) {
            pushImageCmd.withTag(tag.get())
            logger.quiet "Pushing image with name '${imageName.get()}:${tag.get()}'."
        } else {
            logger.quiet "Pushing image with name '${imageName.get()}'."
        }

        pushImageCmd.withAuthConfig(resolveAuthConfig(imageName.get()))

        PushImageResultCallback callback = new PushImageResultCallback() {
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

        pushImageCmd.exec(callback).awaitSuccess()
    }
}
