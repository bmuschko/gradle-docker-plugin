package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.ResultCallback

class DockerInspectNetwork extends DockerExistingNetwork implements ResultCallback {
    @Override
    void runRemoteCommand(final dockerClient) {
        logger.quiet "Inspecting network '${getNetworkId()}'."
        final network = dockerClient.inspectNetworkCmd().withNetworkId(getNetworkId()).exec()

        if (onNext) {
            onNext.call(network)
        }
    }
}
