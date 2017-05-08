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
    String execId

    void targetExecId(Closure execId) {
        conventionMapping.execId = execId
    }

    @Input
    String getExecId() {
        execId
    }

    @Override
    void runRemoteCommand(Object dockerClient) {
        logger.quiet "Inspecting exec with ID '${getExecId()}'."
        def result = dockerClient.inspectExecCmd(getExecId()).exec()
        if (onNext) {
            onNext.call(result)
        } else {
            logger.quiet("Exec ID: {}", result.id)
            logger.quiet("Container ID: {}", result.containerID)
            logger.quiet("Is running: {}", result.running)
            logger.quiet("Exit code: {}", result.exitCode)
        }
    }
}
