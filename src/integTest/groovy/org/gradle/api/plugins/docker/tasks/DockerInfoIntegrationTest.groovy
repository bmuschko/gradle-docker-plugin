package org.gradle.api.plugins.docker.tasks

import org.gradle.api.Task
import org.gradle.api.tasks.TaskExecutionException

class DockerInfoIntegrationTest extends DockerTaskIntegrationTest {
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = project.task('info', type: DockerInfo)
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection to $SERVER_URL refused")
    }
}
