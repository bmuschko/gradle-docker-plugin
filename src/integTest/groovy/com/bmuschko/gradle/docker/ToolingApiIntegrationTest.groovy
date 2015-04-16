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

import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

class ToolingApiIntegrationTest extends AbstractIntegrationTest {
    File buildFile

    def setup() {
        buildFile = createNewFile(projectDir, 'build.gradle')

        buildFile << """
buildscript {
    dependencies {
        classpath files('../classes/main')
    }
}

apply plugin: com.bmuschko.gradle.docker.DockerRemoteApiPlugin

repositories {
    mavenCentral()
}
"""

        setupDockerServerUrl()
        setupDockerCertPath()
        setupDockerPrivateRegistryUrl()
    }

    private void setupDockerServerUrl() {
        String dockerServerUrl = TestConfiguration.dockerServerUrl

        if(dockerServerUrl) {
            buildFile << """
docker.url = '$dockerServerUrl'
"""
        }
    }

    private void setupDockerCertPath() {
        File dockerCertPath = TestConfiguration.dockerCertPath

        if(dockerCertPath) {
            buildFile << """
docker.certPath = new File('$dockerCertPath.canonicalPath')
"""
        }
    }

    private void setupDockerPrivateRegistryUrl() {
        String dockerPrivateRegistryUrl = TestConfiguration.dockerPrivateRegistryUrl

        if(dockerPrivateRegistryUrl) {
            buildFile << """
docker.registryCredentials {
    url = '$dockerPrivateRegistryUrl'
}"""
        }
    }

    protected GradleInvocationResult runTasks(String... tasks) {
        ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()

        try {
            BuildLauncher builder = connection.newBuild()
            OutputStream outputStream = new ByteArrayOutputStream()
            builder.setStandardOutput(outputStream)
            builder.forTasks(tasks).run()
            GradleProject gradleProject = connection.getModel(GradleProject)
            return new GradleInvocationResult(project: gradleProject, output: new String(outputStream.toByteArray(), 'UTF-8'))
        }
        finally {
            connection?.close()
        }
    }

    protected String createUniqueImageId() {
        "gradle/${UUID.randomUUID().toString().replaceAll('-', '')}"
    }

    protected String createUniqueContainerName() {
        UUID.randomUUID().toString().replaceAll('-', '')
    }
}
