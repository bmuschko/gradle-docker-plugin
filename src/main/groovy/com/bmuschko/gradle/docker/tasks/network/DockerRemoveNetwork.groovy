package com.bmuschko.gradle.docker.tasks.network

import com.github.dockerjava.api.DockerClient

class DockerRemoveNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand(DockerClient dockerClient) {
        logger.quiet "Removing network '${networkId.get()}'."
        dockerClient.removeNetworkCmd(networkId.get()).exec()
    }
}
