package org.gradle.api.plugins.docker.tasks

import org.gradle.api.Task

class DockerInfoIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('info', type: DockerInfo)
    }
}
