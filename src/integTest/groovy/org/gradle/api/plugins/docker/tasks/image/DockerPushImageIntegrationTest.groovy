package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest

class DockerPushImageIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('pushImage', type: DockerPushImage) {
            imageId = 'bmuschko/myImage'
            username = 'bmuschko'
            password = 'pwd'
            email = 'benjamin.muschko@gmail.com'
        }
    }
}
