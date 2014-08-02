package org.gradle.api.plugins.docker.tasks

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task
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

        project.apply plugin: 'docker'

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
