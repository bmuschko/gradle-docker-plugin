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

import org.gradle.api.file.FileCollection

class DockerExtension {
    FileCollection classpath
    String url = 'http://localhost:2375'
    File certPath
    String apiVersion

    DockerRegistryCredentials registryCredentials

    void registryCredentials(Closure closure) {
        registryCredentials = new DockerRegistryCredentials()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = registryCredentials
        closure()
    }
}