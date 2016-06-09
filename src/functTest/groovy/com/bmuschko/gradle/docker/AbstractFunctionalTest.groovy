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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractFunctionalTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile

    String dockerServerUrl

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')

        buildFile << """
            plugins {
                id 'com.bmuschko.docker-remote-api'
            }
			
            repositories {
                mavenCentral()
            }
        """

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
                docker.registryCredentials {
                    url = '$dockerPrivateRegistryUrl'
                }
            """
        }
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).build()
    }

    protected BuildResult buildAndFail(String... arguments) {
        createAndConfigureGradleRunner(arguments).buildAndFail()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        GradleRunner.create().withProjectDir(projectDir).withArguments(arguments).withPluginClasspath()
    }

    protected String createUniqueImageId() {
        "gradle/${generateRandomUUID()}"
    }

    protected String createUniqueContainerName() {
        generateRandomUUID()
    }

    private String generateRandomUUID() {
        UUID.randomUUID().toString().replaceAll('-', '')
    }
}
