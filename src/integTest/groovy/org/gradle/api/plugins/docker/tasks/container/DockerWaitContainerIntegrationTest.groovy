package org.gradle.api.plugins.docker.tasks.container

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest

class DockerWaitContainerIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('waitContainer', type: DockerWaitContainer) {
            containerId = 'busybox'
        }
    }
}
