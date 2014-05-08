package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input

class DockerKillContainer extends AbstractDockerTask {
    @Input
    String containerId

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Killing container with ID ${getContainerId()}."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.kill(getContainerId())
    }
}
