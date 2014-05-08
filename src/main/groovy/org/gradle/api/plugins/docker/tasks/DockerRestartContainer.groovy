package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input

class DockerRestartContainer extends AbstractDockerTask {
    @Input
    String containerId

    @Input
    Integer timeout

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Restarting container with ID ${getContainerId()}."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.restart(getContainerId(), getTimeout())
    }
}
