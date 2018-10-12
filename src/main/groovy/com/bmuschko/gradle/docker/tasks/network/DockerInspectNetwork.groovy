package com.bmuschko.gradle.docker.tasks.network

class DockerInspectNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand(final dockerClient) {
        logger.quiet "Inspecting network '${networkId.get()}'."
        final network = dockerClient.inspectNetworkCmd().withNetworkId(networkId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(network)
        }
    }
}
