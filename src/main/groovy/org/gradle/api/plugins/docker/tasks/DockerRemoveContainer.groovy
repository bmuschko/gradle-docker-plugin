package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input

class DockerRemoveContainer extends AbstractDockerTask {
    @Input
    String containerId

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Removing container with ID ${getContainerId()}."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.removeContainer(getContainerId())
    }
}
