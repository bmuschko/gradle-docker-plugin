package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest
import org.gradle.api.tasks.TaskExecutionException

class DockerCommitImageIntegrationTest extends DockerTaskIntegrationTest {
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = project.task('commitImage', type: DockerCommitImage) {
            containerId = 'busybox'
        }
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection to $DockerTaskIntegrationTest.SERVER_URL refused")
    }
}
