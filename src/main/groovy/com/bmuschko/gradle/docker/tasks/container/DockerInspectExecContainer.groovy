package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.InspectExecResponse
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input

import java.util.concurrent.Callable

/**
 * Inspects task executed inside container
 * with {@link DockerExecContainer} command.
 */
@CompileStatic
class DockerInspectExecContainer extends AbstractDockerRemoteApiTask {

    /**
     * The ID name of exec used to perform operation. The exec for the provided
     * ID has to be created and started first.
     */
    @Input
    final Property<String> execId = project.objects.property(String)

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name
     * @see #targetExecId(Callable)
     * @see #targetExecId(Provider)
     */
    void targetExecId(String execId) {
        this.execId.set(execId)
    }

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name as Callable
     * @see #targetExecId(String)
     * @see #targetExecId(Provider)
     */
    void targetExecId(Callable<String> execId) {
        targetExecId(project.provider(execId))
    }

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name as Provider
     * @see #targetExecId(String)
     * @see #targetExecId(Callable)
     */
    void targetExecId(Provider<String> execId) {
        this.execId.set(execId)
    }

    @Override
    void runRemoteCommand() {
        logger.quiet "Inspecting exec with ID '${execId.get()}'."
        InspectExecResponse result = dockerClient.inspectExecCmd(execId.get()).exec()
        if (nextHandler) {
            nextHandler.execute(result)
        } else {
            logger.quiet("Exec ID: {}", result.id)
            logger.quiet("Container ID: {}", result.containerID)
            logger.quiet("Is running: {}", result.running)
            logger.quiet("Exit code: {}", result.exitCode)
        }
    }
}
