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
package org.gradle.api.plugins.docker.tasks

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.docker.DockerBasePlugin
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Specification

abstract class DockerTaskIntegrationTest extends Specification {
    static final String SERVER_URL = 'http://localhost:2375'
    File projectDir = new File('build/integTest')
    Project project

    def setup() {
        deleteProjectDir()
        projectDir.mkdirs()

        project = ProjectBuilder.builder().withProjectDir(projectDir).build()

        project.apply plugin: DockerBasePlugin

        project.repositories {
            mavenCentral()
        }

        project.docker {
            serverUrl = SERVER_URL
        }
    }

    def cleanup() {
        deleteProjectDir()
    }

    private void deleteProjectDir() {
        if(projectDir.exists()) {
            FileUtils.deleteDirectory(projectDir)
        }
    }

    static boolean isServerUrlReachable() {
        URL url = new URL("$SERVER_URL/info")
        HttpURLConnection connection = url.openConnection()
        connection.requestMethod = 'GET'
        connection.responseCode == HttpURLConnection.HTTP_OK
    }

    @IgnoreIf({ DockerTaskIntegrationTest.isServerUrlReachable() })
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = createAndConfigureTask()
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection to $DockerTaskIntegrationTest.SERVER_URL refused")
    }

    abstract Task createAndConfigureTask()
}
