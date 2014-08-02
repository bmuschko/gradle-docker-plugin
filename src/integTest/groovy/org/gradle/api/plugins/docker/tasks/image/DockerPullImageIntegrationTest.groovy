package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest
import org.gradle.api.tasks.TaskExecutionException

class DockerPullImageIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('pullImage', type: DockerPullImage) {
            imageId = 'bmuschko/myImage'
        }
    }
}
