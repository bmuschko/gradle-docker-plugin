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

abstract class AbstractGroovyDslFunctionalTest extends AbstractFunctionalTest {

    String dockerServerUrl

    def setup() {
        setupBuildfile()
    }

    protected void setupBuildfile() {
        buildFile << """
            plugins {
                id 'com.bmuschko.docker-remote-api'
            }

            repositories {
                mavenCentral()
            }
        """

        configureRemoteApiPlugin()
    }

    protected void configureRemoteApiPlugin() {
        setupDockerServerUrl()
        setupDockerCertPath()
        setupDockerPrivateRegistryUrl()
    }

    private void setupDockerServerUrl() {
        dockerServerUrl = TestConfiguration.dockerHost

        if (dockerServerUrl) {
            buildFile << """
                docker.url = '$dockerServerUrl'
            """
        }
    }

    private void setupDockerCertPath() {
        File dockerCertPath = TestConfiguration.dockerCertPath

        if (dockerCertPath) {
            buildFile << """
                docker.certPath = new File('$dockerCertPath.canonicalPath')
            """
        }
    }

    private void setupDockerPrivateRegistryUrl() {
        String dockerPrivateRegistryUrl = TestConfiguration.dockerPrivateRegistryUrl

        if (dockerPrivateRegistryUrl) {
            buildFile << """
                docker {
                    registryCredentials {
                        url = '$dockerPrivateRegistryUrl'
                    }
                }
            """
        }
    }

    protected static String createUniqueImageId() {
        "gradle/${generateRandomUUID()}"
    }

    protected static String createUniqueContainerName() {
        generateRandomUUID()
    }

    protected static String createUniqueNetworkName() {
        generateRandomUUID()
    }

    private static String generateRandomUUID() {
        UUID.randomUUID().toString().replaceAll('-', '')
    }

    @Override
    String getBuildFileName() {
        'build.gradle'
    }

    @Override
    String getSettingsFileName() {
        'settings.gradle'
    }
}
