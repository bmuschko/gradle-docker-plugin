package com.bmuschko.gradle.docker.tasks.network

import groovy.transform.CompileStatic

@CompileStatic
class DockerRemoveNetwork extends DockerExistingNetwork {
    @Override
    void runRemoteCommand() {
        logger.quiet "Removing network '${networkId.get()}'."
        dockerClient.removeNetworkCmd(networkId.get()).exec()
    }
}
