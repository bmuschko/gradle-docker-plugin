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

import com.bmuschko.gradle.docker.response.image.PushImageResponseHandler
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PushImageResponseHandlerTest extends Specification {
    PushImageResponseHandler responseHandler = new PushImageResponseHandler()
    Logger logger = Mock()

    def setup() {
        responseHandler.logger = logger
    }

    def "Handle successful response"() {
        given:
        String response = """{"status":"The push refers to a repository [bmuschko/busybox] (len: 2)"}
{"status":"Sending image list"}
{"status":"Pushing repository bmuschko/busybox (2 tags)"}
{"status":"Pushing","progressDetail":{},"id":"511136ea3c5a"}{"status":"Image already pushed, skipping","progressDetail":{},"id":"511136ea3c5a"}{"status":"Pushing","progressDetail":{},"id":"df7546f9f060"}{"status":"Image already pushed, skipping","progressDetail":{},"id":"df7546f9f060"}{"status":"Pushing","progressDetail":{},"id":"e433a6c5b276"}{"status":"Image already pushed, skipping","progressDetail":{},"id":"e433a6c5b276"}{"status":"Pushing","progressDetail":{},"id":"e72ac664f4f0"}{"status":"Image already pushed, skipping","progressDetail":{},"id":"e72ac664f4f0"}{"status":"Pushing","progressDetail":{},"id":"5a0753bc925c"}{"status":"Image already pushed, skipping","progressDetail":{},"id":"5a0753bc925c"}{"status":"Pushing tag for rev [5a0753bc925c] on {https://cdn-registryCredentials-1.docker.io/v1/repositories/bmuschko/busybox/tags/gradle-1419704044596}"}
{"status":"Pushing","progressDetail":{},"id":"7924f0818409"}{"status":"Buffering to disk","progressDetail":{"current":3072,"start":1419836333},"progress":"3.072 kB","id":"7924f0818409"}{"status":"Buffering to disk","progressDetail":{"start":1419836333},"id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":512,"total":3072,"start":1419836333},"progress":"[========\\u003e                                          ]    512 B/3.072 kB 5s","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":1024,"total":3072,"start":1419836333},"progress":"[================\\u003e                                  ] 1.024 kB/3.072 kB 2s","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":1536,"total":3072,"start":1419836333},"progress":"[=========================\\u003e                         ] 1.536 kB/3.072 kB 1s","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":2048,"total":3072,"start":1419836333},"progress":"[=================================\\u003e                 ] 2.048 kB/3.072 kB 0","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":2560,"total":3072,"start":1419836333},"progress":"[=========================================\\u003e         ]  2.56 kB/3.072 kB 0","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":3072,"total":3072,"start":1419836333},"progress":"[==================================================\\u003e] 3.072 kB/3.072 kB","id":"7924f0818409"}{"status":"Pushing","progressDetail":{"current":3072,"total":3072,"start":1419836333},"progress":"[==================================================\\u003e] 3.072 kB/3.072 kB","id":"7924f0818409"}{"status":"Image successfully pushed","progressDetail":{},"id":"7924f0818409"}{"status":"Pushing tag for rev [7924f0818409] on {https://cdn-registryCredentials-1.docker.io/v1/repositories/bmuschko/busybox/tags/latest}"}"""

        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        responseHandler.handle(inputStream)

        then:
        1 * logger.quiet('The push refers to a repository [bmuschko/busybox] (len: 2).')
        1 * logger.quiet('Sending image list.')
        1 * logger.quiet('Pushing repository bmuschko/busybox (2 tags).')
        1 * logger.quiet('Pushing 511136ea3c5a.')
        1 * logger.quiet('Pushing 7924f0818409.')
    }

    def "Handle error response"() {
        given:
        String response = """{"status":"The push refers to a repository [bmuschko/busybox] (len: 2)"}
{"status":"Sending image list"}
{"errorDetail":{"message":"Error: Status 401 trying to push repository bmuschko/busybox: \\"\\""},"error":"Error: Status 401 trying to push repository bmuschko/busybox: \\"\\""}"""
        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        responseHandler.handle(inputStream)

        then:
        1 * logger.quiet('The push refers to a repository [bmuschko/busybox] (len: 2).')
        1 * logger.quiet('Sending image list.')
        Throwable t = thrown(GradleException)
        t.message == 'Error: Status 401 trying to push repository bmuschko/busybox: ""'
    }
}
