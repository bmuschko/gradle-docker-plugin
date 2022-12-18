package com.bmuschko.gradle.docker.tasks.network;

public class DockerRemoveNetwork extends DockerExistingNetwork {
    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Removing network '" + getNetworkId().get() + "'.");
        getDockerClient().removeNetworkCmd(getNetworkId().get()).exec();
    }
}
