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
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.annotation.Nullable

/**
 * The extension for configuring the Docker communication via the remote API through the {@link DockerRemoteApiPlugin}.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     registryCredentials {
 *         username = 'bmuschko'
 *         password = 'pwd'
 *     }
 * }
 * </pre>
 */
@CompileStatic
class DockerRegistryCredentials {

    /**
     * The registry URL used as default value for the property {@link #url}.
     */
    public static final String DEFAULT_URL = 'https://index.docker.io/v1/'

    /**
     * Registry URL needed to push images.
     * <p>
     * Defaults to "https://index.docker.io/v1/".
     */
    @Input
    final Property<String> url

    /**
     * Registry username needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    final Property<String> username

    /**
     * Registry password needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    final Property<String> password

    /**
     * Registry email address needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    final Property<String> email

    DockerRegistryCredentials(ObjectFactory objectFactory) {
        url = objectFactory.property(String)
        url.set(DEFAULT_URL)
        username = objectFactory.property(String)
        password = objectFactory.property(String)
        email = objectFactory.property(String)
    }

    /**
     * Translates the Docker registry credentials into a {@link PasswordCredentials}.
     *
     * @since 4.0.0
     */
    PasswordCredentials asPasswordCredentials() {
        new PasswordCredentials() {
            @Override
            String getUsername() {
                DockerRegistryCredentials.this.username.get()
            }

            @Override
            void setUsername(@Nullable String userName) {
                DockerRegistryCredentials.this.username.set(userName)
            }

            @Override
            String getPassword() {
                DockerRegistryCredentials.this.password.get()
            }

            @Override
            void setPassword(@Nullable String password) {
                DockerRegistryCredentials.this.password.set(password)
            }
        }
    }
}
