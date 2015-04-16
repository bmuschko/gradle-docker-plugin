package com.bmuschko.gradle.docker.tasks.container

class DockerInspectContainer extends DockerExistingContainer {
    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Inspecting container with ID '${getContainerId()}'."
        def container = dockerClient.inspectContainerCmd(getContainerId()).exec()
        logger.quiet "Image ID : $container.imageId"
        logger.quiet "Name     : $container.name"
    }
}
