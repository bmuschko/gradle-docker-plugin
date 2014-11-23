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
package com.bmuschko.gradle.docker.utils

import spock.lang.Specification

class ResponseUtilsTest extends Specification {
    static final String SUCCESSFUL_BUILD_IMAGE_RESPONSE = '{"stream":"Successfully built d21c999f11f8\\n"}'
    static final String FAILED_BUILD_IMAGE_RESPONSE = '{"errorDetail":{"message":"The command [/bin/sh -c apt-get install -q -y openjdk-7-jre-headless \u0026\u0026 apt-get clean] returned a non-zero code: 100"},"error":"The command [/bin/sh -c apt-get install -q -y openjdk-7-jre-headless \u0026\u0026 apt-get clean] returned a non-zero code: 100"}'

    def "Indicates successful build image response"() {
        expect:
        ResponseUtils.isSuccessfulBuildImageResponse(SUCCESSFUL_BUILD_IMAGE_RESPONSE)
    }

    def "Indicates failed build image response"() {
        expect:
        !ResponseUtils.isSuccessfulBuildImageResponse(FAILED_BUILD_IMAGE_RESPONSE)
    }

    def "Parse image ID from successful build image response"() {
        when:
        String imageId = ResponseUtils.parseImageIdFromBuildImageResponse(SUCCESSFUL_BUILD_IMAGE_RESPONSE)

        then:
        imageId == 'd21c999f11f8'
    }

    def "Parse image ID from failed build image response"() {
        when:
        String imageId = ResponseUtils.parseImageIdFromBuildImageResponse(FAILED_BUILD_IMAGE_RESPONSE)

        then:
        !imageId
    }
}
