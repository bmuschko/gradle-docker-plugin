package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.CreateNetworkCmd
import com.github.dockerjava.api.command.CreateNetworkResponse
import com.github.dockerjava.api.model.Network
import groovy.transform.CompileStatic
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
    final Property<String> networkName = project.objects.property(String)

    /**
     * The id of the created network.
     */
    @Internal
    final Property<String> networkId = project.objects.property(String)

    /**
     * @since X.X.X
     */
    @Nested
    final Ipam ipam

    @Inject
    DockerCreateNetwork(ObjectFactory objectFactory) {
        ipam = objectFactory.newInstance(Ipam, objectFactory)
    }

    void runRemoteCommand() {
        logger.quiet "Creating network '${networkName.get()}'."

        CreateNetworkCmd networkCmd = dockerClient.createNetworkCmd().withName(networkName.get())

        List<Network.Ipam.Config> ipamConfig = ipam.createIpamConfigs()
        if (!ipamConfig.empty) {
            networkCmd.withIpam(new Network.Ipam().withConfig(ipamConfig))
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
        private static final String SUBNET = "subnet"

        @Input
        @Optional
        ListProperty<Map> config

        @Inject
        Ipam(ObjectFactory objectFactory) {
            config = objectFactory.listProperty(Map)
        }

        List<Network.Ipam.Config> createIpamConfigs() {
            List<Network.Ipam.Config> configList = new ArrayList<>()

            if (config.getOrNull()) {
                for (Map<String, String> configMap in config.get()) {
                    Network.Ipam.Config ipamConfig = new Network.Ipam.Config()

                    if (configMap.containsKey(SUBNET)) {
                        ipamConfig.withSubnet(configMap.get(SUBNET).toString())
                    }

                    configList.add(ipamConfig)
                }
            }

            return configList
        }
    }
}
