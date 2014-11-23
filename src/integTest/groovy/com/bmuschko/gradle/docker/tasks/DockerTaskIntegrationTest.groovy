package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.ProjectBuilderIntegrationTest
import org.gradle.api.Task
import org.gradle.api.tasks.TaskExecutionException
import spock.lang.IgnoreIf

abstract class DockerTaskIntegrationTest extends ProjectBuilderIntegrationTest {
    @IgnoreIf({ AbstractIntegrationTest.isDockerServerInfoUrlReachable() })
    def "Throws ConnectionException for unreachable Docker server"() {
        when:
        Task task = createAndConfigureTask()
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection refused")
    }

    abstract Task createAndConfigureTask()
}
