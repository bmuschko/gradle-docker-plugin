package com.bmuschko.gradle.docker.tasks.network

class DockerRemoveNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand(com.github.dockerjava.api.DockerClient dockerClient) {
        logger.quiet "Removing network '${networkId.get()}'."
        dockerClient.removeNetworkCmd(networkId.get()).exec()
    }
}
