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
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.nio.charset.StandardCharsets

class PullImageResponseHandler implements ResponseHandler<Void> {
    Logger logger = Logging.getLogger(PullImageResponseHandler)
    private final JsonSlurper slurper = new JsonSlurper()

    @Override
    Void handle(InputStream response) {
        Reader reader = new InputStreamReader(response, StandardCharsets.UTF_8)

        reader.eachLine { line ->
            def json = slurper.parseText(line)

            if(json.get('status')) {
                if(json.id) {
                    logger.quiet "${json.status} with ID ${json.id}."
                }
                else {
                    logger.quiet "${json.status}."
                }
            }
            else if(json.get('error')) {
                throw new GradleException(json.error)
            }
        }
    }
}
