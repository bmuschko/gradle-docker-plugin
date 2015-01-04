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
package com.bmuschko.gradle.docker.response

import groovy.json.JsonSlurper
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.nio.charset.StandardCharsets

class BuildImageResponseHandler implements ResponseHandler<String> {
    static final String SUCCESS_OUTPUT = 'Successfully built'
    private final JsonSlurper slurper = new JsonSlurper()
    Logger logger = Logging.getLogger(PushImageResponseHandler)

    @Override
    String handle(InputStream response) {
        Reader reader = new InputStreamReader(response, StandardCharsets.UTF_8)

        reader.eachLine { line ->
            def json = slurper.parseText(line)

            if(line.contains('stream')) {
                String stream = json.stream
                logger.info stream

                if(isSuccessfulStreamIndicator(stream)) {
                    String imageId = parseImageIdFromStream(stream)
                    logger.quiet "Created image with ID '$imageId'."
                    return imageId
                }
            }
            else if(line.contains('error')) {
                logger.error "${json.error}."
            }
        }
    }

    private boolean isSuccessfulStreamIndicator(String stream) {
        stream.contains(SUCCESS_OUTPUT)
    }

    private String parseImageIdFromStream(String stream) {
        (stream - SUCCESS_OUTPUT).trim()
    }
}
