package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input

import java.util.concurrent.Callable

/**
 * Inspects task executed inside container
 * with {@link DockerExecContainer} command.
 */
class DockerInspectExecContainer extends AbstractDockerRemoteApiTask {

    /**
     * Exec ID used to perform operation. The exec for the provided
     * ID has to be created and started first.
     */
    @Input
    final Property<String> execId = project.objects.property(String)

    void targetExecId(String execId) {
        this.execId.set(execId)
    }

    void targetExecId(Callable<String> execId) {
        targetExecId(project.provider(execId))
    }

    void targetExecId(Provider<String> execId) {
        this.execId.set(execId)
    }

    @Override
    void runRemoteCommand(Object dockerClient) {
        logger.quiet "Inspecting exec with ID '${execId.get()}'."
        def result = _runRemoteCommand(dockerClient, execId.get())
        if (nextHandler) {
            nextHandler.execute(result)
        } else {
            logger.quiet("Exec ID: {}", result.id)
            logger.quiet("Container ID: {}", result.containerID)
            logger.quiet("Is running: {}", result.running)
            logger.quiet("Exit code: {}", result.exitCode)
        }
    }

    // overloaded method to get the response of a given "exec"
    // from potentially outside of this context or in a sub-class
    // without all the extra baggage the default method brings.
    static def _runRemoteCommand(dockerClient, String executionId) {
        dockerClient.inspectExecCmd(executionId).exec()
    }
}
