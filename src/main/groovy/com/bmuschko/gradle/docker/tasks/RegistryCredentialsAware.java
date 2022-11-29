package com.bmuschko.gradle.docker.tasks;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.Nested;

public interface RegistryCredentialsAware extends Task {
    /**
     * The target Docker registry credentials for usage with a task.
     */
    @Nested
    DockerRegistryCredentials getRegistryCredentials();

    /**
     * Configures the target Docker registry credentials for use with a task.
     *
     * @param action The action against the Docker registry credentials
     * @since 6.0.0
     */
    void registryCredentials(Action<? super DockerRegistryCredentials> action);
}
