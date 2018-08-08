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
import org.gradle.api.file.FileCollection

@CompileStatic
class DockerExtension {
    private final Project project
    FileCollection classpath
    String url
    File certPath
    String apiVersion
    DockerRegistryCredentials registryCredentials

    DockerExtension(Project project) {
        this.project = project
        this.url = getDefaultDockerUrl()
        this.certPath = getDefaultDockerCert()
    }

    void registryCredentials(Closure closure) {
        registryCredentials = new DockerRegistryCredentials()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = registryCredentials
        closure()
    }

    String getDefaultDockerUrl() {
        String dockerUrl = System.getenv("DOCKER_HOST")
        if (!dockerUrl) {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win")
            if (!isWindows && new File('/var/run/docker.sock').exists()) {
                dockerUrl = 'unix:///var/run/docker.sock'
            } else {
                if (isWindows && new File("\\\\.\\pipe\\docker_engine").exists()) {
                    // TODO: re-enable once docker-java supports named pipes. Relevant links:
                    //
                    //     https://github.com/bmuschko/gradle-docker-plugin/pull/313
                    //     https://github.com/docker-java/docker-java/issues/765
                    //
                    // dockerUrl = 'npipe:////./pipe/docker_engine'
                    dockerUrl = 'tcp://127.0.0.1:2375'
                } else {
                    dockerUrl = 'tcp://127.0.0.1:2375'
                }
            }
        }
        project.logger.info("Default docker.url set to $dockerUrl")
        dockerUrl
    }

    File getDefaultDockerCert() {
        String dockerCertPath = System.getenv("DOCKER_CERT_PATH")
        if(dockerCertPath) {
            File certFile = new File(dockerCertPath)
            if(certFile.exists()) {
                return certFile
            }
        }
        return null
    }
}
