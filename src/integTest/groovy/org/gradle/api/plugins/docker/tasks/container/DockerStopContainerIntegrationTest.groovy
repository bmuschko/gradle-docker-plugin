package org.gradle.api.plugins.docker.tasks.container

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest

class DockerStopContainerIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('stopContainer', type: DockerStopContainer) {
            containerId = 'busybox'
            timeout = 2
        }
    }
}
