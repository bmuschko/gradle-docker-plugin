package org.gradle.api.plugins.docker.tasks

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerTaskIntegrationTest extends Specification {
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
}
