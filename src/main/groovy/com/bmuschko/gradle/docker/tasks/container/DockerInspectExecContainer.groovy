package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

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
    String execId

    void targetExecId(Closure execId) {
        conventionMapping.execId = execId
    }

    @Override
    void runRemoteCommand(Object dockerClient) {
        logger.quiet "Inspecting exec with ID '${getExecId()}'."
        def result = _runRemoteCommand(dockerClient, getExecId())
        if (onNext) {
            onNext.call(result)
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
