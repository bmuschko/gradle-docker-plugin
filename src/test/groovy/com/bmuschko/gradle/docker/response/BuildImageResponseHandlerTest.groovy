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

class BuildImageResponseHandlerTest extends Specification {
    BuildImageResponseHandler responseHandler = new BuildImageResponseHandler()
    Logger logger = Mock()

    def setup() {
        responseHandler.logger = logger
    }

    def "Handle successful response"() {
        String response = """{"stream":"Step 0 : FROM dockerfile/java:openjdk-7-jre\\n"}
{"stream":" ---\\u003e 13a4762ffb1c\\n"}
{"stream":"Step 1 : MAINTAINER Benjamin Muschko \\"benjamin.muschko@gmail.com\\"\\n"}
{"stream":" ---\\u003e Using cache\\n"}
{"stream":" ---\\u003e 612899607f3d\\n"}
{"stream":"Step 2 : ADD integTest-1.0.tar /\\n"}
{"stream":" ---\\u003e 44046bff2441\\n"}
{"stream":"Removing intermediate container fcd02e7b7d17\\n"}
{"stream":"Step 3 : ENTRYPOINT /integTest-1.0/bin/integTest\\n"}
{"stream":" ---\\u003e Running in c904dfd24cbf\\n"}
{"stream":" ---\\u003e 5893a16ec4e4\\n"}
{"stream":"Removing intermediate container c904dfd24cbf\\n"}
{"stream":"Step 4 : EXPOSE 9090\\n"}
{"stream":" ---\\u003e Running in ae1699fe1007\\n"}
{"stream":" ---\\u003e bffa8586c96c\\n"}
{"stream":"Removing intermediate container ae1699fe1007\\n"}
{"stream":"Successfully built bffa8586c96c\\n"}"""

        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        String imageId = responseHandler.handle(inputStream)

        then:
        1 * logger.info('Step 0 : FROM dockerfile/java:openjdk-7-jre\n')
        1 * logger.info(' ---\u003e 13a4762ffb1c\n')
        1 * logger.info('Step 1 : MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"\n')
        1 * logger.info(' ---\u003e Using cache\n')
        1 * logger.info(' ---\u003e 612899607f3d\n')
        1 * logger.info('Step 2 : ADD integTest-1.0.tar /\n')
        1 * logger.info(' ---\u003e 44046bff2441\n')
        1 * logger.info('Removing intermediate container fcd02e7b7d17\n')
        1 * logger.info('Step 3 : ENTRYPOINT /integTest-1.0/bin/integTest\n')
        1 * logger.info(' ---\u003e Running in c904dfd24cbf\n')
        1 * logger.info(' ---\u003e 5893a16ec4e4\n')
        1 * logger.info('Removing intermediate container c904dfd24cbf\n')
        1 * logger.info('Step 4 : EXPOSE 9090\n')
        1 * logger.info(' ---\u003e Running in ae1699fe1007\n')
        1 * logger.info(' ---\u003e bffa8586c96c\n')
        1 * logger.info('Removing intermediate container ae1699fe1007\n')
        1 * logger.info('Successfully built bffa8586c96c\n')
        1 * logger.quiet("Created image with ID 'bffa8586c96c'.")
        imageId == 'bffa8586c96c'
    }

    def "Handle error response"() {
        String response = """{"errorDetail":{"message":"The command [/bin/sh -c apt-get install -q -y openjdk-7-jre-headless && apt-get clean] returned a non-zero code: 100"},"error":"The command [/bin/sh -c apt-get install -q -y openjdk-7-jre-headless && apt-get clean] returned a non-zero code: 100"}"""
        InputStream inputStream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))

        when:
        String imageId = responseHandler.handle(inputStream)

        then:
        1 * logger.error('The command [/bin/sh -c apt-get install -q -y openjdk-7-jre-headless && apt-get clean] returned a non-zero code: 100.')
        !imageId
    }
}
