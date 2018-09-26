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

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

@CompileStatic
class DockerRegistryCredentials {
    public static final String DEFAULT_URL = 'https://index.docker.io/v1/'

    /**
     * Registry URL needed to push images. Defaults to "https://index.docker.io/v1/".
     */
    @Input
    final Property<String> url

    /**
     * Registry username needed to push images. Defaults to null.
     */
    @Input
    @Optional
    final Property<String> username

    /**
     * Registry password needed to push images. Defaults to null.
     */
    @Input
    @Optional
    final Property<String> password

    /**
     * Registry email address needed to push images. Defaults to null.
     */
    @Input
    @Optional
    final Property<String> email

    DockerRegistryCredentials(Project project) {
        url = project.objects.property(String)
        url.set(DEFAULT_URL)
        username = project.objects.property(String)
        password = project.objects.property(String)
        email = project.objects.property(String)
    }
}
