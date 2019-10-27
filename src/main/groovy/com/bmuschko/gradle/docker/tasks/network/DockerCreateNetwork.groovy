package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.CreateNetworkResponse
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

@CompileStatic
class DockerCreateNetwork extends AbstractDockerRemoteApiTask {
    @Input
    final Property<String> networkId = project.objects.property(String)

    void runRemoteCommand() {
        logger.quiet "Creating network '${networkId.get()}'."
        CreateNetworkResponse network = dockerClient.createNetworkCmd().withName(networkId.get()).exec()

        if (nextHandler) {
            nextHandler.execute(network)
        }
    }
}
