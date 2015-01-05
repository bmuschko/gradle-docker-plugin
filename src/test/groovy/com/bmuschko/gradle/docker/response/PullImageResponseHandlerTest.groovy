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

import org.gradle.api.logging.Logger
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PullImageResponseHandlerTest extends Specification {
    PullImageResponseHandler responseHandler = new PullImageResponseHandler()
    Logger logger = Mock()

    def setup() {
        responseHandler.logger = logger
    }

    def "Handle successful response"() {
        given:
        String response = """{"status":"Pulling repository bmuschko/busybox"}
{"status":"Pulling image (latest) from bmuschko/busybox","progressDetail":{},"id":"eb23d79a7b6c"}{"status":"Pulling image (latest) from bmuschko/busybox, endpoint: https://registry-1.docker.io/v1/","progressDetail":{},"id":"eb23d79a7b6c"}{"status":"Pulling image (gradle-1419704044596) from bmuschko/busybox","progressDetail":{},"id":"5a0753bc925c"}{"status":"Pulling image (gradle-1419704044596) from bmuschko/busybox, endpoint: https://registry-1.docker.io/v1/","progressDetail":{},"id":"5a0753bc925c"}{"status":"Pulling dependent layers","progressDetail":{},"id":"eb23d79a7b6c"}{"status":"Download complete","progressDetail":{},"id":"511136ea3c5a"}{"status":"Download complete","progressDetail":{},"id":"df7546f9f060"}{"status":"Download complete","progressDetail":{},"id":"e433a6c5b276"}{"status":"Download complete","progressDetail":{},"id":"e72ac664f4f0"}{"status":"Download complete","progressDetail":{},"id":"eb23d79a7b6c"}{"status":"Download complete","progressDetail":{},"id":"eb23d79a7b6c"}{"status":"Pulling dependent layers","progressDetail":{},"id":"5a0753bc925c"}{"status":"Download complete","progressDetail":{},"id":"511136ea3c5a"}{"status":"Download complete","progressDetail":{},"id":"df7546f9f060"}{"status":"Download complete","progressDetail":{},"id":"e433a6c5b276"}{"status":"Download complete","progressDetail":{},"id":"e72ac664f4f0"}{"status":"Download complete","progressDetail":{},"id":"5a0753bc925c"}{"status":"Download complete","progressDetail":{},"id":"5a0753bc925c"}{"status":"Status: Image is up to date for bmuschko/busybox"}"""

        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        responseHandler.handle(inputStream)

        then:
        1 * logger.quiet('Pulling repository bmuschko/busybox.')
        1 * logger.quiet('Pulling image (latest) from bmuschko/busybox with ID eb23d79a7b6c.')
    }

    def "Handle error response"() {
        given:
        String response = """{"status":"Pulling repository bmuschko/busybox"}
{"errorDetail":{"message":"Error: image bmuschko/busybox not found"},"error":"Error: image bmuschko/busybox not found"}"""
        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        responseHandler.handle(inputStream)

        then:
        1 * logger.quiet('Pulling repository bmuschko/busybox.')
        1 * logger.error('Error: image bmuschko/busybox not found.')
    }
}
