package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

class DockerCreateNetwork extends AbstractDockerRemoteApiTask {
    @Input
    String networkId

    DockerCreateNetwork() {
        ext.getNetworkId = { networkId }
    }

    void runRemoteCommand(final dockerClient) {
        logger.quiet "Creating network '$networkId'."
        final network = dockerClient.createNetworkCmd().withName(networkId).exec()

        if (onNext) {
            onNext.call(network)
        }
    }
}
