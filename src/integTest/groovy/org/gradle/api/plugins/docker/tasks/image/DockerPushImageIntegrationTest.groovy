package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest
import org.gradle.api.tasks.TaskExecutionException

class DockerPushImageIntegrationTest extends DockerTaskIntegrationTest {
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = project.task('pushImage', type: DockerPushImage) {
            imageId = 'bmuschko/myImage'
            username = 'bmuschko'
            password = 'pwd'
            email = 'benjamin.muschko@gmail.com'
        }
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection to $DockerTaskIntegrationTest.SERVER_URL refused")
    }
}
