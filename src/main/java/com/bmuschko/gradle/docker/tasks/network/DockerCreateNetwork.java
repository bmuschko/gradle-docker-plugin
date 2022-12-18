package com.bmuschko.gradle.docker.tasks.network;

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.model.Network;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class DockerCreateNetwork extends AbstractDockerRemoteApiTask {
    /**
     * The name of the network to be created.
     *
     * @since 6.4.0
     */
    @Input
    public final Property<String> getNetworkName() {
        return networkName;
    }

    private final Property<String> networkName;

    /**
     * The IP address management (IPAM) for managing IP address space on a network.
     *
     * @since 8.0.0
     */
    @Nested
    public final Ipam getIpam() {
        return ipam;
    }

    private final Ipam ipam;

    /**
     * The id of the created network.
     */
    @Internal
    public final Property<String> getNetworkId() {
        return networkId;
    }

    private final Property<String> networkId;


    @Inject
    public DockerCreateNetwork(ObjectFactory objects) {
        networkName = objects.property(String.class);
        networkId = objects.property(String.class);
        ipam = objects.newInstance(Ipam.class);
    }

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Creating network '" + getNetworkName().get() + "'.");
        CreateNetworkCmd networkCmd = getDockerClient().createNetworkCmd().withName(networkName.get());

        if (ipam.getDriver().isPresent() || !ipam.getConfigs().get().isEmpty()) {
            Network.Ipam networkIpam = new Network.Ipam();

            if (ipam.getDriver().isPresent()) {
                networkIpam.withDriver(ipam.getDriver().get());
            }
            if (!ipam.getConfigs().get().isEmpty()) {
                networkIpam.withConfig(ipam.toDockerJavaConfigs());
            }

            networkCmd.withIpam(networkIpam);
        }

        CreateNetworkResponse network = networkCmd.exec();

        if (getNextHandler() != null) {
            getNextHandler().execute(network);
        }

        String createdNetworkId = network.getId();
        networkId.set(createdNetworkId);
        getLogger().quiet("Created network with ID '" + createdNetworkId + "'.");
    }

    public static class Ipam {
        @Input
        @Optional
        public final Property<String> getDriver() {
            return driver;
        }

        private final Property<String> driver;

        @Nested
        public final ListProperty<Config> getConfigs() {
            return configs;
        }

        private final ListProperty<Config> configs;

        @Inject
        public Ipam(ObjectFactory objectFactory) {
            driver = objectFactory.property(String.class);
            configs = objectFactory.listProperty(Config.class);
        }

        List<Network.Ipam.Config> toDockerJavaConfigs() {
            List<Network.Ipam.Config> configList = new ArrayList<Network.Ipam.Config>();

            for (Config c : configs.get()) {
                Network.Ipam.Config ipamConfig = new Network.Ipam.Config();

                if (c.getSubnet() != null) {
                    ipamConfig.withSubnet(c.getSubnet());
                }

                if (c.getIpRange() != null) {
                    ipamConfig.withIpRange(c.getIpRange());
                }

                if (c.getGateway() != null) {
                    ipamConfig.withGateway(c.getGateway());
                }

                if (c.getNetworkID() != null) {
                    ipamConfig.withGateway(c.getNetworkID());
                }

                configList.add(ipamConfig);
            }

            return configList;
        }

        public static class Config {
            @Input
            @Optional
            public String getSubnet() {
                return subnet;
            }

            public void setSubnet(String subnet) {
                this.subnet = subnet;
            }

            private String subnet;

            @Input
            @Optional
            public String getIpRange() {
                return ipRange;
            }

            public void setIpRange(String ipRange) {
                this.ipRange = ipRange;
            }

            private String ipRange;

            @Input
            @Optional
            public String getGateway() {
                return gateway;
            }

            public void setGateway(String gateway) {
                this.gateway = gateway;
            }

            private String gateway;

            @Input
            @Optional
            public String getNetworkID() {
                return networkID;
            }

            public void setNetworkID(String networkID) {
                this.networkID = networkID;
            }

            private String networkID;
        }
    }
}
