package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.CreateNetworkResponse
import groovy.transform.CompileStatic
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

@CompileStatic
class DockerCreateNetwork extends AbstractDockerRemoteApiTask {
    /**
     * The name of the network to be created.
     *
     * @since 6.4.0
     */
    @Input
    final Property<String> networkName = project.objects.property(String)

    /**
     * The id of the created network.
     */
    @Internal
    final Property<String> networkId = project.objects.property(String)

    void runRemoteCommand() {
        logger.quiet "Creating network '${networkName.get()}'."
        CreateNetworkResponse network = dockerClient.createNetworkCmd().withName(networkName.get()).exec()

        if (nextHandler) {
            nextHandler.execute(network)
        }

        String createdNetworkId = network.id
        networkId.set(createdNetworkId)
        logger.quiet "Created network with ID '$createdNetworkId'."
    }
}
