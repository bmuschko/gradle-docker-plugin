package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

abstract class DockerExistingNetwork extends AbstractDockerRemoteApiTask {
    /**
     * The name of the network to perform the operation on.
     */
    @Input
    String networkId

    void targetNetworkId(Closure networkId) {
        conventionMapping.networkId = networkId
    }
}
