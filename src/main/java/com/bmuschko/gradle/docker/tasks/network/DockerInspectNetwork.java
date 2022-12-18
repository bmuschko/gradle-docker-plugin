package com.bmuschko.gradle.docker.tasks.network;

import com.github.dockerjava.api.model.Network;

public class DockerInspectNetwork extends DockerExistingNetwork {
    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Inspecting network '" + getNetworkId().get() + "'.");
        Network network = getDockerClient().inspectNetworkCmd().withNetworkId(getNetworkId().get()).exec();

        if (getNextHandler() != null) {
            getNextHandler().execute(network);
        }
    }
}
