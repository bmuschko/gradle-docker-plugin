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

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

abstract class AbstractFunctionalTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile

    static final String TEST_IMAGE = "alpine"
    static final String TEST_IMAGE_TAG = "3.4"
    static final String TEST_IMAGE_WITH_TAG = "${TEST_IMAGE}:${TEST_IMAGE_TAG}"

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
        setupDockerTemplateFile()

        buildFile << """
            task dockerVersion(type: com.bmuschko.gradle.docker.tasks.DockerVersion)
        """

        when:
        BuildResult result = build('dockerVersion')

        then:
        result.output.contains('Retrieving Docker version.')
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

    private void setupDockerTemplateFile() {
        File source = new File(TestConfiguration.class.getClassLoader().getResource("Dockerfile.template").toURI())
        if (source.exists()) {
            File resourcesDir = new File(projectDir, 'src/main/docker/')
            if (resourcesDir.mkdirs()) {
                if (Files.copy(source.toPath(),Paths.get(projectDir.path, 'src/main/docker/Dockerfile.template')).toFile().length() != source.length()) {
                    throw new GradleException("File could not be successfully copied")
                }
            } else {
                throw new IOException("can not create the directory ${resourcesDir.absolutePath}")
            }
        }
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).build()
    }

    protected BuildResult buildAndFail(String... arguments) {
        createAndConfigureGradleRunner(arguments).buildAndFail()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        def args = ['--stacktrace']
        if (arguments) {
            args.addAll(arguments)
        }
        GradleRunner.create().withProjectDir(projectDir).withArguments(args).withPluginClasspath()
    }

    protected String createUniqueImageId() {
        "gradle/${generateRandomUUID()}"
    }

    protected String createUniqueContainerName() {
        generateRandomUUID()
    }

    protected String createUniqueNetworkName() {
        generateRandomUUID()
    }

    private String generateRandomUUID() {
        UUID.randomUUID().toString().replaceAll('-', '')
    }
}
