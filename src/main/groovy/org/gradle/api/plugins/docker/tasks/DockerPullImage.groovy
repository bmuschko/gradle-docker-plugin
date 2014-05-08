package org.gradle.api.plugins.docker.tasks

import org.gradle.api.tasks.Input

class DockerPullImage extends AbstractDockerTask {
    @Input
    String imageId

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Pulling image ID ${getImageId()}."
        def dockerClient = getDockerClient(classLoader)
        dockerClient.pull(getImageId())
    }
}
