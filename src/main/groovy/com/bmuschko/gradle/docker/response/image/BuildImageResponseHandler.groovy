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
package com.bmuschko.gradle.docker.response.image

import com.bmuschko.gradle.docker.response.ResponseHandler
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class BuildImageResponseHandler implements ResponseHandler<String, Object> {
    private final Logger logger

    BuildImageResponseHandler() {
        this(Logging.getLogger(BuildImageResponseHandler))
    }

    private BuildImageResponseHandler(Logger logger) {
        this.logger = logger
    }

    @Override
    String handle(Object response) {
        String imageId = response.awaitImageId()
        logger.quiet "Created image with ID '$imageId'."
        return imageId
    }
}
