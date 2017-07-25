package com.bmuschko.gradle.docker.tasks.network

class DockerInspectNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand(final dockerClient) {
        logger.quiet "Inspecting network '${getNetworkId()}'."
        final network = dockerClient.inspectNetworkCmd().withNetworkId(getNetworkId()).exec()

        if (onNext) {
            onNext.call(network)
        }
    }
}
