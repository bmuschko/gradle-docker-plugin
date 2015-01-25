package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.ProjectBuilderIntegrationTest
import org.gradle.api.Task
import org.gradle.api.tasks.TaskExecutionException
import spock.lang.IgnoreIf

import static com.bmuschko.gradle.docker.AbstractIntegrationTest.isDockerServerInfoUrlReachable

abstract class DockerTaskIntegrationTest extends ProjectBuilderIntegrationTest {
    @IgnoreIf({ isDockerServerInfoUrlReachable() })
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
