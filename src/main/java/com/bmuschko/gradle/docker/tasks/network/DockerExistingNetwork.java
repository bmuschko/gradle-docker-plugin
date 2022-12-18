package com.bmuschko.gradle.docker.tasks.network;

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.util.concurrent.Callable;

public abstract class DockerExistingNetwork extends AbstractDockerRemoteApiTask {

    /**
     * The ID or name of the network to perform the operation on. The network for the provided ID has to be created first.
     */
    @Input
    public final Property<String> getNetworkId() {
        return networkId;
    }

    private final Property<String> networkId = getProject().getObjects().property(String.class);

    /**
     * Sets the target network ID or name.
     *
     * @param networkId Network ID or name
     * @see #targetNetworkId(Callable)
     * @see #targetNetworkId(Provider)
     */
    public void targetNetworkId(String networkId) {
        this.networkId.set(networkId);
    }

    /**
     * Sets the target network ID or name.
     *
     * @param networkId Network ID or name as Callable
     * @see #targetNetworkId(String)
     * @see #targetNetworkId(Provider)
     */
    public void targetNetworkId(Callable<String> networkId) {
        targetNetworkId(getProject().provider(networkId));
    }

    /**
     * Sets the target network ID or name.
     *
     * @param networkId Network ID or name as Provider
     * @see #targetNetworkId(String)
     * @see #targetNetworkId(Callable)
     */
    public void targetNetworkId(Provider<String> networkId) {
        this.networkId.set(networkId);
    }
}
