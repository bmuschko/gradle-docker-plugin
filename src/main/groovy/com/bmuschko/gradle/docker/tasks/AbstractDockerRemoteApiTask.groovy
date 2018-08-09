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
package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.utils.ThreadContextClassLoader
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

abstract class AbstractDockerRemoteApiTask extends AbstractReactiveStreamsTask {

    /**
     * Docker remote API server URL. Defaults to "http://localhost:2375".
     */
    @Input
    @Optional
    String url

    /**
     * Path to the <a href="https://docs.docker.com/articles/https/">Docker certificate and key</a>.
     */
    @InputDirectory
    @Optional
    File certPath

    /**
     * The docker remote api version
     */
    @Input
    @Optional
    String apiVersion

    @Internal
    ThreadContextClassLoader threadContextClassLoader

    @Override
    void runReactiveStream() {
        runInDockerClassPath { dockerClient ->
            runRemoteCommand(dockerClient)
        }
    }

    void runInDockerClassPath(Closure closure) {
        threadContextClassLoader.withContext(createDockerClientConfig(), closure)
    }

    private DockerClientConfiguration createDockerClientConfig() {
        DockerClientConfiguration dockerClientConfig = new DockerClientConfiguration()
        dockerClientConfig.url = getUrl()
        dockerClientConfig.certPath = getCertPath()
        dockerClientConfig.apiVersion = getApiVersion()
        dockerClientConfig
    }

    abstract void runRemoteCommand(dockerClient)
}
