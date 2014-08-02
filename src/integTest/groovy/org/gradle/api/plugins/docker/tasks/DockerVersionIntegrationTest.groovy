package org.gradle.api.plugins.docker.tasks

import org.gradle.api.Task

class DockerVersionIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('version', type: DockerVersion)
    }
}
