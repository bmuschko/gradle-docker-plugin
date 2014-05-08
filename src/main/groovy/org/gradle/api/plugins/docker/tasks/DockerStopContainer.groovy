package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input

class DockerStopContainer extends AbstractDockerTask {
    @Input
    String containerId

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Stopping container with ID ${getContainerId()}."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.stopContainer(getContainerId())
    }
}
