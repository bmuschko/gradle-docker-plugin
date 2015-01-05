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

import com.bmuschko.gradle.docker.DockerRegistry
import com.bmuschko.gradle.docker.response.PushImageResponseHandler
import com.bmuschko.gradle.docker.response.ResponseHandler
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryAware
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

class DockerPushImage extends AbstractDockerRemoteApiTask implements RegistryAware {
    private final ResponseHandler<Void> responseHandler = new PushImageResponseHandler()

    @Input
    String imageName

    @Input
    @Optional
    String tag

    /**
     * Docker registry for pushing containers.
     */
    @Nested
    @Optional
    DockerRegistry registry

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Pushing image with name '${getImageName()}'."
        def pushImageCmd = dockerClient.pushImageCmd(getImageName())

        if(getTag()) {
            pushImageCmd.withTag(getTag())
        }

        if(getRegistry()) {
            def authConfig = threadContextClassLoader.createAuthConfig(getRegistry())
            pushImageCmd.withAuthConfig(authConfig)
        }

        InputStream response = pushImageCmd.exec()
        responseHandler.handle(response)
    }
}
