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

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.command.PullImageResultCallback
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractFunctionalTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile

    static final String TEST_IMAGE = "alpine"
    static final String TEST_IMAGE_TAG = "3.4"
    static final String TEST_IMAGE_WITH_TAG = "${TEST_IMAGE}:${TEST_IMAGE_TAG}"

    String dockerServerUrl

    @Shared
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("unix:///var/run/docker.sock")
        .build()

    @Shared
    DockerClient dockerClient = DockerClientBuilder.getInstance(config).build()

    @Shared
    private def dockerContainersBeforeTests = [] as Set

    @Shared
    private def dockerImagesBeforeTests = [] as Set

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

        buildFile << """
            task dockerVersion(type: com.bmuschko.gradle.docker.tasks.DockerVersion)
        """

        when:
        BuildResult result = build('dockerVersion')

        then:
        result.output.contains('Retrieving Docker version.')
    }

    def setupSpec() {
        dockerImagesBeforeTests = dockerClient.listImagesCmd().exec() as Set
        dockerContainersBeforeTests = dockerClient.listContainersCmd().withShowAll(true).exec() as Set
    }

    def cleanupSpec() {
        removeDockerContainers()
        removeDockerImages()
    }

    private void removeDockerContainers() {

        def dockerContainersAfterTests = dockerClient.listContainersCmd().withShowAll(true).exec() as Set

        def removableDockerContainerIds = dockerContainersAfterTests.collect { it.id } - dockerContainersBeforeTests.collect { it.id }

        println("Removing ${removableDockerContainerIds.size()} containers: ${removableDockerContainerIds}")
        removableDockerContainerIds.each {
            try {
                dockerClient.removeContainerCmd(it)
                    .withForce(true)
                    .exec()
            } catch (NotFoundException ex) {
                println("Exception while removing container: ${ex.message} ")
            }
        }

        println("Docker containers removed.")
    }

    private void removeDockerImages() {

        def dockerImagesCreatedAfterTests = dockerClient.listImagesCmd().exec() as Set

        def removableDockerImageIds = dockerImagesCreatedAfterTests.collect { it.id }  - dockerImagesBeforeTests.collect { it.id }

        println("Removing ${removableDockerImageIds.size()} images: ${removableDockerImageIds}")

        removableDockerImageIds.each {
            try {
                dockerClient.removeImageCmd(it)
                    .withForce(true)
                    .exec()
            } catch (NotFoundException ex) {
                println("Exception while removing image: ${ex.message} ")
            }
        }

        println("Docker images removed.")
    }

    protected void startDockerRegistryContainer() {

        def containers = dockerClient.listContainersCmd().exec()
        println("Active dockerContainersBeforeTests: ${containers}")

        boolean isRegistryAlreadyRunning

        isRegistryAlreadyRunning = containers.find {
            return it?.names?.contains("/registry")
        }

        if (!isRegistryAlreadyRunning) {
            println("Docker registry container is not currently running.")

            boolean isDockerRegistryImageAlreadyPulled = dockerClient.listImagesCmd().exec().find {
                return it.repoTags.contains('registry:latest')
            }

            if (!isDockerRegistryImageAlreadyPulled) {
                println("Pulling docker registry image.")
                dockerClient.pullImageCmd('registry:latest').exec(new PullImageResultCallback()).awaitCompletion()
            }

            CreateContainerResponse registryContainer = dockerClient.createContainerCmd('registry')
                .withPortBindings(new Ports(PortBinding.parse("5000:5000")))
                .withName('registry')
                .exec()

            println("Starting docker registry container.")
            dockerClient.startContainerCmd(registryContainer.getId()).exec()
        }

    }

    protected void removeDockerRegistryContainer() {
        println("Removing docker registry container.")
        try {
            dockerClient.removeContainerCmd('registry')
                .withForce(true)
                .exec()
        } catch (NotFoundException ex) {
            println("Exception while removing container: ${ex.message} ")
        }
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
        def args = ['--stacktrace']
        if (arguments) {
            args.addAll(arguments)
        }
        GradleRunner.create().withProjectDir(projectDir).withArguments(args).withPluginClasspath()
    }

    protected String createUniqueImageId() {
        "gradle/${generateRandomUUID()}"
    }

    protected final String createUniqueContainerName() {
        generateRandomUUID()
    }

    protected String createUniqueNetworkName() {
        generateRandomUUID()
    }

    private String generateRandomUUID() {
        UUID.randomUUID().toString().replaceAll('-', '')
    }
}
