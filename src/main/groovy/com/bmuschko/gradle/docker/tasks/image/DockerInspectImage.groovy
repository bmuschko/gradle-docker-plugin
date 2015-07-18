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

import com.bmuschko.gradle.docker.response.image.InspectImageResponseHandler
import com.bmuschko.gradle.docker.response.ResponseHandler

class DockerInspectImage extends DockerExistingImage {
    private ResponseHandler<Void, Object> responseHandler = new InspectImageResponseHandler()

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Inspecting image for with ID '${getImageId()}'."
        def image = dockerClient.inspectImageCmd(getImageId()).exec()
        responseHandler.handle(image)
    }

    void setResponseHandler(ResponseHandler<Void, Object> responseHandler) {
        this.responseHandler = responseHandler
    }
}
