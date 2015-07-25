package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.api.Task
import org.gradle.api.tasks.TaskExecutionException
import spock.lang.Requires

abstract class DockerTaskIntegrationTest extends AbstractIntegrationTest {
    @Requires({ !TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = createAndConfigureTask()
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection refused") || t.cause.message.contains("https protocol is not supported")
    }

    abstract Task createAndConfigureTask()
}
