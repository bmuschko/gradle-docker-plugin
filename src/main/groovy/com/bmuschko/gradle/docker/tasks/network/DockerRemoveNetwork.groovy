package com.bmuschko.gradle.docker.tasks.network

class DockerRemoveNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand(final dockerClient) {
        final networkId = getNetworkId()
        logger.quiet "Removing network '$networkId'."
        dockerClient.removeNetworkCmd(networkId).exec()
    }
}
