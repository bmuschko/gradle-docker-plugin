package com.bmuschko.gradle.docker.tasks.network

import com.github.dockerjava.api.model.Network

class DockerInspectNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand() {
        logger.quiet "Inspecting network '${networkId.get()}'."
        Network network = dockerClient.inspectNetworkCmd().withNetworkId(networkId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(network)
        }
    }
}
