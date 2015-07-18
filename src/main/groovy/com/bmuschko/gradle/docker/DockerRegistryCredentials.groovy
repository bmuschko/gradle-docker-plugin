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
package com.bmuschko.gradle.docker

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerRegistryCredentials {
    public static final String DEFAULT_URL = 'https://index.docker.io/v1/'

    /**
     * Registry URL needed to push images. Defaults to "https://index.docker.io/v1/".
     */
    @Input
    String url = DEFAULT_URL

    /**
     * Registry username needed to push images. Defaults to null.
     */
    @Input
    @Optional
    String username

    /**
     * Registry password needed to push images. Defaults to null.
     */
    @Input
    @Optional
    String password

    /**
     * Registry email address needed to push images. Defaults to null.
     */
    @Input
    @Optional
    String email
}
