package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.CreateNetworkCmd
import com.github.dockerjava.api.command.CreateNetworkResponse
import com.github.dockerjava.api.model.Network
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

import javax.inject.Inject

@CompileStatic
class DockerCreateNetwork extends AbstractDockerRemoteApiTask {
    /**
     * The name of the network to be created.
     *
     * @since 6.4.0
     */
    @Input
    final Property<String> networkName

    @Nested
    final Ipam ipam

    /**
     * The id of the created network.
     */
    @Internal
    final Property<String> networkId

    @Inject
    DockerCreateNetwork(ObjectFactory objectFactory) {
        networkName = project.objects.property(String)
        networkId = project.objects.property(String)
        ipam = objectFactory.newInstance(Ipam, objectFactory)
    }

    void runRemoteCommand() {
        logger.quiet "Creating network '${networkName.get()}'."
        CreateNetworkCmd networkCmd = dockerClient.createNetworkCmd().withName(networkName.get())

        if (ipam.driver.isPresent() || !ipam.configs.get().isEmpty()) {
            Network.Ipam networkIpam = new Network.Ipam()

            if (ipam.driver.isPresent()) {
                networkIpam.withDriver(ipam.driver.get())
            }
            if (!ipam.configs.get().isEmpty()) {
                networkIpam.withConfig(ipam.toDockerJavaConfigs())
            }

            networkCmd.withIpam(networkIpam)
        }

        CreateNetworkResponse network = networkCmd.exec()

        if (nextHandler) {
            nextHandler.execute(network)
        }

        String createdNetworkId = network.id
        networkId.set(createdNetworkId)
        logger.quiet "Created network with ID '$createdNetworkId'."
    }

    static class Ipam {
        @Input
        @Optional
        final Property<String> driver

        @Nested
        final ListProperty<Config> configs

        @Inject
        Ipam(ObjectFactory objectFactory) {
            driver = objectFactory.property(String)
            configs = objectFactory.listProperty(Config).empty()
        }

        @PackageScope
        List<Network.Ipam.Config> toDockerJavaConfigs() {
            List<Network.Ipam.Config> configList = new ArrayList<>()

            for (Config c : configs.get()) {
                Network.Ipam.Config ipamConfig = new Network.Ipam.Config()

                if (c.subnet) {
                    ipamConfig.withSubnet(c.subnet)
                }
                if (c.ipRange) {
                    ipamConfig.withIpRange(c.ipRange)
                }
                if (c.gateway) {
                    ipamConfig.withGateway(c.gateway)
                }
                if (c.networkID) {
                    ipamConfig.withGateway(c.networkID)
                }

                configList.add(ipamConfig)
            }

            configList
        }

        static class Config {
            @Input
            @Optional
            String subnet

            @Input
            @Optional
            String ipRange

            @Input
            @Optional
            String gateway

            @Input
            @Optional
            String networkID
        }
    }
}
