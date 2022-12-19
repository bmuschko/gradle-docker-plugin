package com.bmuschko.gradle.docker.tasks.container;

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.github.dockerjava.api.command.InspectExecResponse;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.util.concurrent.Callable;

/**
 * Inspects task executed inside container
 * with {@link DockerExecContainer} command.
 */
public class DockerInspectExecContainer extends AbstractDockerRemoteApiTask {

    /**
     * The ID name of exec used to perform operation. The exec for the provided
     * ID has to be created and started first.
     */
    @Input
    public final Property<String> getExecId() {
        return execId;
    }

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name
     * @see #targetExecId(Callable)
     * @see #targetExecId(Provider)
     */
    public void targetExecId(String execId) {
        this.execId.set(execId);
    }

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name as Callable
     * @see #targetExecId(String)
     * @see #targetExecId(Provider)
     */
    public void targetExecId(Callable<String> execId) {
        targetExecId(getProject().provider(execId));
    }

    /**
     * Sets the target exec ID or name.
     *
     * @param execId Exec ID or name as Provider
     * @see #targetExecId(String)
     * @see #targetExecId(Callable)
     */
    public void targetExecId(Provider<String> execId) {
        this.execId.set(execId);
    }

    private final Property<String> execId = getProject().getObjects().property(String.class);

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Inspecting exec with ID '" + getExecId().get() + "'.");
        InspectExecResponse result = getDockerClient().inspectExecCmd(execId.get()).exec();
        if (getNextHandler() != null) {
            getNextHandler().execute(result);
        } else {
            getLogger().quiet("Exec ID: {}", result.getId());
            getLogger().quiet("Container ID: {}", result.getContainerID());
            getLogger().quiet("Is running: {}", result.isRunning());
            getLogger().quiet("Exit code: {}", result.getExitCode());
        }
    }
}
