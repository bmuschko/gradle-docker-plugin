package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

abstract class DockerExistingNetwork extends AbstractDockerRemoteApiTask {
    /**
     * The name of the network to perform the operation on.
     */
    @Internal
    String networkId

    void targetNetworkId(Closure networkId) {
        conventionMapping.networkId = networkId
    }

    @Input
    String getNetworkId() {
        networkId
    }
}
