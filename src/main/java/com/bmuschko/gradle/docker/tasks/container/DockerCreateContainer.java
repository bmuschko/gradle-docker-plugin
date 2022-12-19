/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks.container;

import com.bmuschko.gradle.docker.internal.RegularFileToStringTransformer;
import com.bmuschko.gradle.docker.tasks.image.DockerExistingImage;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.*;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class DockerCreateContainer extends DockerExistingImage {
    @Input
    @Optional
    public final Property<String> getContainerName() {
        return containerName;
    }

    @Input
    @Optional
    public final Property<String> getHostName() {
        return hostName;
    }

    @Input
    @Optional
    public final Property<String> getIpv4Address() {
        return ipv4Address;
    }

    @Input
    @Optional
    public final ListProperty<String> getPortSpecs() {
        return portSpecs;
    }

    @Input
    @Optional
    public final Property<String> getUser() {
        return user;
    }

    @Input
    @Optional
    public final Property<Boolean> getStdinOpen() {
        return stdinOpen;
    }

    @Input
    @Optional
    public final Property<Boolean> getStdinOnce() {
        return stdinOnce;
    }

    @Input
    @Optional
    public final Property<Boolean> getAttachStdin() {
        return attachStdin;
    }

    @Input
    @Optional
    public final Property<Boolean> getAttachStdout() {
        return attachStdout;
    }

    @Input
    @Optional
    public final Property<Boolean> getAttachStderr() {
        return attachStderr;
    }

    @Input
    @Optional
    public final MapProperty<String, String> getEnvVars() {
        return envVars;
    }

    @Input
    @Optional
    public final ListProperty<String> getCmd() {
        return cmd;
    }

    @Input
    @Optional
    public final ListProperty<String> getEntrypoint() {
        return entrypoint;
    }

    @Input
    @Optional
    public final ListProperty<String> getNetworkAliases() {
        return networkAliases;
    }

    @Input
    @Optional
    public final Property<String> getImage() {
        return image;
    }

    @Input
    @Optional
    public final ListProperty<String> getVolumes() {
        return volumes;
    }

    @Input
    @Optional
    public final Property<String> getWorkingDir() {
        return workingDir;
    }

    @Input
    public final ListProperty<ExposedPort> getExposedPorts() {
        return exposedPorts;
    }

    @Input
    @Optional
    public final Property<Boolean> getTty() {
        return tty;
    }

    @Input
    @Optional
    public final Property<String> getPid() {
        return pid;
    }

    @Input
    @Optional
    public final MapProperty<String, String> getLabels() {
        return labels;
    }

    /**
     * Output file containing the container ID of the container created.
     * Defaults to "$buildDir/.docker/$taskpath-containerId.txt".
     * If path contains ':' it will be replaced by '_'.
     */
    @OutputFile
    public final RegularFileProperty getContainerIdFile() {
        return containerIdFile;
    }

    /**
     * The ID of the container created. The value of this property requires the task action to be executed.
     */
    @Internal
    public final Property<String> getContainerId() {
        return containerId;
    }

    @Input
    @Optional
    public final Property<String> getMacAddress() {
        return macAddress;
    }

    /**
     * The target platform in the format {@code os[/arch[/variant]]}, for example {@code linux/s390x} or {@code darwin}.
     *
     * @since 7.1.0
     */
    @Input
    @Optional
    public final Property<String> getPlatform() {
        return platform;
    }

    @Nested
    public final HostConfig getHostConfig() {
        return hostConfig;
    }

    @Nested
    public final HealthCheckConfig getHealthCheck() {
        return healthCheck;
    }

    private final Property<String> containerName = getProject().getObjects().property(String.class);
    private final Property<String> hostName = getProject().getObjects().property(String.class);
    private final Property<String> ipv4Address = getProject().getObjects().property(String.class);
    private final ListProperty<String> portSpecs = getProject().getObjects().listProperty(String.class);
    private final Property<String> user = getProject().getObjects().property(String.class);
    private final Property<Boolean> stdinOpen = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> stdinOnce = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> attachStdin = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> attachStdout = getProject().getObjects().property(Boolean.class);
    private final Property<Boolean> attachStderr = getProject().getObjects().property(Boolean.class);
    private final MapProperty<String, String> envVars = getProject().getObjects().mapProperty(String.class, String.class);
    private final ListProperty<String> cmd = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> entrypoint = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> networkAliases = getProject().getObjects().listProperty(String.class);
    private final Property<String> image = getProject().getObjects().property(String.class);
    private final ListProperty<String> volumes = getProject().getObjects().listProperty(String.class);
    private final Property<String> workingDir = getProject().getObjects().property(String.class);
    private final ListProperty<ExposedPort> exposedPorts = getProject().getObjects().listProperty(ExposedPort.class);
    private final Property<Boolean> tty = getProject().getObjects().property(Boolean.class);
    private final Property<String> pid = getProject().getObjects().property(String.class);
    private final MapProperty<String, String> labels = getProject().getObjects().mapProperty(String.class, String.class);
    private final RegularFileProperty containerIdFile = getProject().getObjects().fileProperty();
    private final Property<String> containerId = getProject().getObjects().property(String.class);
    private final Property<String> macAddress = getProject().getObjects().property(String.class);
    private final Property<String> platform = getProject().getObjects().property(String.class);
    private final HostConfig hostConfig;
    private final HealthCheckConfig healthCheck;

    @Inject
    public DockerCreateContainer(ObjectFactory objectFactory) {
        hostConfig = objectFactory.newInstance(HostConfig.class);
        healthCheck = objectFactory.newInstance(HealthCheckConfig.class);
        stdinOpen.convention(false);
        stdinOnce.convention(false);
        attachStdin.convention(false);
        attachStdout.convention(false);
        attachStderr.convention(false);
        tty.convention(false);

        containerId.convention(containerIdFile.map(new RegularFileToStringTransformer()));

        final String safeTaskPath = getPath().replaceFirst("^:", "").replaceAll(":", "_");
        containerIdFile.convention(getProject().getLayout().getBuildDirectory().file(".docker/" + safeTaskPath + "-containerId.txt"));

        getOutputs().upToDateWhen(upToDateWhenSpec);
    }

    private Spec<Task> upToDateWhenSpec = new Spec<Task>() {
        @Override
        public boolean isSatisfiedBy(Task element) {
            File file = getContainerIdFile().get().getAsFile();
            if (file.exists()) {
                try {
                    String fileContainerId;
                    try {
                        fileContainerId = Files.readString(file.toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    getDockerClient().inspectContainerCmd(fileContainerId).exec();
                    return true;
                } catch (DockerException ignored) {
                }
            }
            return false;
        }
    };

    @Override
    public void runRemoteCommand() throws IOException {
        CreateContainerCmd containerCommand = getDockerClient().createContainerCmd(getImageId().get());
        setContainerCommandConfig(containerCommand);
        CreateContainerResponse container = containerCommand.exec();
        final String orNull = containerName.getOrNull();
        final String localContainerName = orNull != null ? orNull : container.getId();
        getLogger().quiet("Created container with ID '" + localContainerName + "'.");
        Files.writeString(containerIdFile.get().getAsFile().toPath(), container.getId());
        if (getNextHandler() != null) {
            getNextHandler().execute(container);
        }
    }

    public void exposePorts(String internetProtocol, List<Integer> ports) {
        exposedPorts.add(new ExposedPort(internetProtocol, ports));
    }

    public void withEnvVar(String key, String value) {
        envVars.put(key, value);
    }

    private static HealthCheck getOrCreateHealthCheck(CreateContainerCmd containerCommand) {
        if (containerCommand.getHealthcheck() == null) {
            containerCommand.withHealthcheck(new HealthCheck());
        }
        return containerCommand.getHealthcheck();
    }

    private void setContainerCommandConfig(CreateContainerCmd containerCommand) {
        if (containerName.getOrNull() != null) {
            containerCommand.withName(containerName.get());
        }

        if (hostName.getOrNull() != null) {
            containerCommand.withHostName(hostName.get());
        }

        if (ipv4Address.getOrNull() != null) {
            containerCommand.withIpv4Address(ipv4Address.get());
        }

        if (portSpecs.getOrNull() != null && !portSpecs.get().isEmpty()) {
            containerCommand.withPortSpecs(portSpecs.get());
        }

        if (user.getOrNull() != null) {
            containerCommand.withUser(user.get());
        }

        if (hostConfig.getGroups().getOrNull() != null && !hostConfig.getGroups().get().isEmpty()) {
            containerCommand.getHostConfig().withGroupAdd(hostConfig.getGroups().get());
        }

        if (Boolean.TRUE.equals(stdinOpen.getOrNull())) {
            containerCommand.withStdinOpen(stdinOpen.get());
        }

        if (Boolean.TRUE.equals(stdinOnce.getOrNull())) {
            containerCommand.withStdInOnce(stdinOnce.get());
        }

        if (hostConfig.getMemory().getOrNull() != null) {
            containerCommand.getHostConfig().withMemory(hostConfig.getMemory().get());
        }

        if (hostConfig.getMemorySwap().getOrNull() != null) {
            containerCommand.getHostConfig().withMemorySwap(hostConfig.getMemorySwap().get());
        }

        if (hostConfig.getCpuset().getOrNull() != null) {
            containerCommand.getHostConfig().withCpusetCpus(hostConfig.getCpuset().get());
        }

        if (Boolean.TRUE.equals(attachStdin.getOrNull())) {
            containerCommand.withAttachStdin(attachStdin.get());
        }

        if (Boolean.TRUE.equals(attachStdout.getOrNull())) {
            containerCommand.withAttachStdout(attachStdout.get());
        }

        if (Boolean.TRUE.equals(attachStderr.getOrNull())) {
            containerCommand.withAttachStderr(attachStderr.get());
        }

        // marshall map into list
        if (envVars.getOrNull() != null && !envVars.get().isEmpty()) {
            containerCommand.withEnv(envVars.get().entrySet().stream().map(entry -> entry.getKey() +"="+entry.getValue()).collect(Collectors.toList()));
        }

        if (cmd.getOrNull() != null && !cmd.get().isEmpty()) {
            containerCommand.withCmd(cmd.get());
        }

        if (entrypoint.getOrNull() != null && !entrypoint.get().isEmpty()) {
            containerCommand.withEntrypoint(entrypoint.get());
        }

        if (hostConfig.getDns().getOrNull() != null && !hostConfig.getDns().get().isEmpty()) {
            containerCommand.getHostConfig().withDns(hostConfig.getDns().get());
        }

        if (hostConfig.getNetwork().getOrNull() != null) {
            containerCommand.getHostConfig().withNetworkMode(hostConfig.getNetwork().get());
        }

        if (networkAliases.getOrNull() != null && !networkAliases.get().isEmpty()) {
            containerCommand.withAliases(networkAliases.get());
        }

        if (image.getOrNull() != null) {
            containerCommand.withImage(image.get());
        }

        if (volumes.getOrNull() != null && !volumes.get().isEmpty()) {
            List<Volume> createdVolumes = volumes.get().stream().map(Volume::parse).collect(Collectors.toList());
            containerCommand.withVolumes(createdVolumes);
        }

        if (hostConfig.getLinks().getOrNull() != null && !hostConfig.getLinks().get().isEmpty()) {
            List<Link> createdLinks = hostConfig.links.get().stream().map(Link::parse).collect(Collectors.toList());
            containerCommand.getHostConfig().withLinks(createdLinks.toArray(Link[]::new));
        }

        if (hostConfig.getVolumesFrom().getOrNull() != null && !hostConfig.getVolumesFrom().get().isEmpty()) {
            List<VolumesFrom> createdVolumes = hostConfig.volumesFrom.get().stream().map(VolumesFrom::new).collect(Collectors.toList());
            containerCommand.getHostConfig().withVolumesFrom(createdVolumes);
        }

        if (workingDir.getOrNull() != null) {
            containerCommand.withWorkingDir(workingDir.get());
        }

        if (exposedPorts.getOrNull() != null && !exposedPorts.get().isEmpty()) {
            List<com.github.dockerjava.api.model.ExposedPort> allPorts = exposedPorts.get().stream().flatMap(exposedPort ->
                    exposedPort.getPorts().stream().map(port -> new com.github.dockerjava.api.model.ExposedPort(port, InternetProtocol.parse(exposedPort.getInternetProtocol().toLowerCase())))).collect(Collectors.toList());
            containerCommand.withExposedPorts(allPorts);
        }

        if (hostConfig.getPortBindings().getOrNull() != null && !hostConfig.getPortBindings().get().isEmpty()) {
            List<PortBinding> createdPortBindings = hostConfig.portBindings.get().stream().map(PortBinding::parse).collect(Collectors.toList());
            containerCommand.getHostConfig().withPortBindings(new Ports(createdPortBindings.toArray(PortBinding[]::new)));
        }

        if (Boolean.TRUE.equals(hostConfig.getPublishAll().getOrNull())) {
            containerCommand.getHostConfig().withPublishAllPorts(hostConfig.getPublishAll().get());
        }

        if (hostConfig.getBinds().getOrNull() != null && !hostConfig.getBinds().get().isEmpty()) {
            List<Bind> createdBinds = hostConfig.binds.get().entrySet().stream().map(it -> Bind.parse(it.getKey() + ":" + it.getValue())).collect(Collectors.toList());
            containerCommand.getHostConfig().withBinds(createdBinds);
        }

        if (hostConfig.getTmpFs().getOrNull() != null && !hostConfig.getTmpFs().get().isEmpty()) {
            containerCommand.getHostConfig().withTmpFs(hostConfig.getTmpFs().get());
        }

        if (hostConfig.getExtraHosts().getOrNull() != null && !hostConfig.getExtraHosts().get().isEmpty()) {
            containerCommand.getHostConfig().withExtraHosts(hostConfig.getExtraHosts().get().toArray(String[]::new));
        }

        if (hostConfig.getLogConfig().getOrNull() != null) {
            com.github.dockerjava.api.model.LogConfig.LoggingType type = com.github.dockerjava.api.model.LogConfig.LoggingType.fromValue(hostConfig.getLogConfig().get().getType());
            com.github.dockerjava.api.model.LogConfig config = new com.github.dockerjava.api.model.LogConfig(type, hostConfig.getLogConfig().get().getConfig());
            containerCommand.getHostConfig().withLogConfig(config);
        }

        if (Boolean.TRUE.equals(hostConfig.getPrivileged().getOrNull())) {
            containerCommand.getHostConfig().withPrivileged(hostConfig.getPrivileged().get());
        }

        if (hostConfig.getRestartPolicy().getOrNull() != null) {
            containerCommand.getHostConfig().withRestartPolicy(RestartPolicy.parse(hostConfig.getRestartPolicy().get()));
        }

        if (hostConfig.getCapAdd().getOrNull() != null && !hostConfig.getCapAdd().get().isEmpty()) {
            Capability[] capabilities = hostConfig.getCapAdd().get().stream().map(Capability::valueOf).toArray(Capability[]::new);
            containerCommand.getHostConfig().withCapAdd(capabilities);
        }

        if (hostConfig.getCapDrop().getOrNull() != null && !hostConfig.getCapDrop().get().isEmpty()) {
            Capability[] capabilities = hostConfig.getCapDrop().get().stream().map(Capability::valueOf).toArray(Capability[]::new);
            containerCommand.getHostConfig().withCapDrop(capabilities);
        }

        if (pid.getOrNull() != null) {
            containerCommand.getHostConfig().withPidMode(pid.get());
        }

        if (hostConfig.getDevices().getOrNull() != null && !hostConfig.getDevices().get().isEmpty()) {
            List<Device> createdDevices = hostConfig.getDevices().get().stream().map(Device::parse).collect(Collectors.toList());
            containerCommand.getHostConfig().withDevices(createdDevices);
        }

        if (Boolean.TRUE.equals(tty.getOrNull())) {
            containerCommand.withTty(tty.get());
        }

        if (hostConfig.getShmSize().getOrNull() != null) { // 0 is valid input
            containerCommand.getHostConfig().withShmSize(hostConfig.getShmSize().get());
        }

        if (hostConfig.getAutoRemove().getOrNull() != null) {
            containerCommand.getHostConfig().withAutoRemove(hostConfig.getAutoRemove().get());
        }

        if (labels.getOrNull() != null && !labels.get().isEmpty()) {
            containerCommand.withLabels(labels.get());
        }

        if (macAddress.getOrNull() != null) {
            containerCommand.withMacAddress(macAddress.get());
        }

        if (platform.getOrNull() != null && !platform.get().isEmpty()) {
            containerCommand.withPlatform(platform.get());
        }

        if (hostConfig.getIpcMode().getOrNull() != null) {
            containerCommand.getHostConfig().withIpcMode(hostConfig.getIpcMode().get());
        }

        if (hostConfig.getSysctls().getOrNull() != null && !hostConfig.getSysctls().get().isEmpty()) {
            containerCommand.getHostConfig().withSysctls(hostConfig.getSysctls().get());
        }

        if (healthCheck.getInterval().getOrNull() != null) {
            getOrCreateHealthCheck(containerCommand).withInterval(healthCheck.getInterval().get());
        }

        if (healthCheck.getTimeout().getOrNull() != null) {
            getOrCreateHealthCheck(containerCommand).withTimeout(healthCheck.getTimeout().get());
        }

        if (healthCheck.getCmd().getOrNull() != null && !healthCheck.getCmd().get().isEmpty()) {
            String command = healthCheck.getCmd().get().size() == 1 ? "CMD-SHELL" : "CMD";
            List<String> test = new ArrayList<>(List.of(command));
            test.addAll(healthCheck.getCmd().get());
            getOrCreateHealthCheck(containerCommand).withTest(test);
        }

        if (healthCheck.getRetries().getOrNull() != null) {
            getOrCreateHealthCheck(containerCommand).withRetries(healthCheck.getRetries().get());
        }

        if (healthCheck.getStartPeriod().getOrNull() != null) {
            getOrCreateHealthCheck(containerCommand).withStartPeriod(healthCheck.getStartPeriod().get());
        }
    }

    public static class ExposedPort implements Serializable {
        private final String internetProtocol;
        private final List<Integer> ports;

        public ExposedPort(String internetProtocol, List<Integer> ports) {
            this.internetProtocol = internetProtocol;
            this.ports = ports;
        }

        public final String getInternetProtocol() {
            return internetProtocol;
        }

        public final List<Integer> getPorts() {
            return ports;
        }
    }

    /**
     * @since 6.0.0
     */
    public static class HostConfig {
        /**
         * A list of additional groups that the container process will run as.
         */
        @Input
        @Optional
        public final ListProperty<String> getGroups() {
            return groups;
        }

        private final ListProperty<String> groups;

        @Input
        @Optional
        public final Property<Long> getMemory() {
            return memory;
        }

        private final Property<Long> memory;

        @Input
        @Optional
        public final Property<Long> getMemorySwap() {
            return memorySwap;
        }

        private final Property<Long> memorySwap;

        @Input
        @Optional
        public final Property<String> getCpuset() {
            return cpuset;
        }

        private final Property<String> cpuset;

        @Input
        @Optional
        public final ListProperty<String> getDns() {
            return dns;
        }

        private final ListProperty<String> dns;

        @Input
        @Optional
        public final Property<String> getNetwork() {
            return network;
        }

        private final Property<String> network;

        @Input
        @Optional
        public final ListProperty<String> getLinks() {
            return links;
        }

        private final ListProperty<String> links;

        @Input
        @Optional
        public final ListProperty<String> getVolumesFrom() {
            return volumesFrom;
        }

        private final ListProperty<String> volumesFrom;

        @Input
        @Optional
        public final ListProperty<String> getPortBindings() {
            return portBindings;
        }

        private final ListProperty<String> portBindings;

        @Input
        @Optional
        public final Property<Boolean> getPublishAll() {
            return publishAll;
        }

        private final Property<Boolean> publishAll;

        @Input
        @Optional
        public final MapProperty<String, String> getBinds() {
            return binds;
        }

        private final MapProperty<String, String> binds;

        /**
         * Docker container tmpfs support.
         * <p>
         * The key of this map is the container target path, the value stores
         * the tmpfs comma-separated options.
         * <p>
         * For example, to create a temporary 50MB writeable non executable filesystem mounted under /data
         * in the container: {@code tmpFs = ['/data': 'rw,noexec,size=50m']}
         * <p>
         * <a href="https://docs.docker.com/storage/tmpfs/">Original documentation</a>
         *
         * @since 8.0.0
         */
        @Input
        @Optional
        public final MapProperty<String, String> getTmpFs() {
            return tmpFs;
        }

        private final MapProperty<String, String> tmpFs;

        @Input
        @Optional
        public final ListProperty<String> getExtraHosts() {
            return extraHosts;
        }

        private final ListProperty<String> extraHosts;

        @Input
        @Optional
        public final Property<LogConfig> getLogConfig() {
            return logConfig;
        }

        private final Property<LogConfig> logConfig;

        @Input
        @Optional
        public final Property<Boolean> getPrivileged() {
            return privileged;
        }

        private final Property<Boolean> privileged;

        @Input
        @Optional
        public final Property<String> getRestartPolicy() {
            return restartPolicy;
        }

        private final Property<String> restartPolicy;

        @Input
        @Optional
        public final ListProperty<String> getDevices() {
            return devices;
        }

        private final ListProperty<String> devices;

        /**
         * @since 8.1.0
         */
        @Input
        @Optional
        public final ListProperty<String> getCapAdd() {
            return capAdd;
        }

        private final ListProperty<String> capAdd;

        /**
         * @since 8.1.0
         */
        @Input
        @Optional
        public final ListProperty<String> getCapDrop() {
            return capDrop;
        }

        private final ListProperty<String> capDrop;

        /**
         * Size of {@code /dev/shm} in bytes.
         * <p>
         * The size must be greater than 0. If omitted the system uses 64MB.
         */
        @Input
        @Optional
        public final Property<Long> getShmSize() {
            return shmSize;
        }

        private final Property<Long> shmSize;

        /**
         * Automatically remove the container when the container's process exits.
         * <p>
         * This has no effect if {@link #restartPolicy} is set.
         */
        @Input
        @Optional
        public final Property<Boolean> getAutoRemove() {
            return autoRemove;
        }

        private final Property<Boolean> autoRemove;

        /**
         * The IPC mode for the container.
         * <ol>
         * <li>{@code none} - Own private IPC namespace, with /dev/shm not mounted.</li>
         * <li>{@code private} - Own private IPC namespace.</li>
         * <li>{@code shareable"} - Own private IPC namespace, with a possibility to share it with other containers.</li>
         * <li>{@code container <_name-or-ID_>} - Join another ("shareable") container’s IPC namespace.</li>
         * <li>{@code host} - Use the host system’s IPC namespace.</li>
         * </ol>
         */
        @Input
        @Optional
        public final Property<String> getIpcMode() {
            return ipcMode;
        }

        private final Property<String> ipcMode;

        /**
         * The namespaced kernel parameters (sysctls) in the container.
         * <p>
         * For example, to turn on IP forwarding in the containers network namespace: {@code sysctls = ['net.ipv4.ip_forward':'1']}
         * <p>
         * <strong>Note:</strong>
         * <ol>
         * <li>Not all sysctls are namespaced.</li>
         * <li>Docker does not support changing sysctls inside of a container that also modify the host system.</li>
         * </ol>
         */
        @Input
        @Optional
        public final MapProperty<String, String> getSysctls() {
            return sysctls;
        }

        private final MapProperty<String, String> sysctls;

        @Inject
        public HostConfig(ObjectFactory objectFactory) {
            groups = objectFactory.listProperty(String.class);
            memory = objectFactory.property(Long.class);
            memorySwap = objectFactory.property(Long.class);
            cpuset = objectFactory.property(String.class);
            dns = objectFactory.listProperty(String.class);
            network = objectFactory.property(String.class);
            links = objectFactory.listProperty(String.class);
            volumesFrom = objectFactory.listProperty(String.class);
            portBindings = objectFactory.listProperty(String.class);
            publishAll = objectFactory.property(Boolean.class);
            publishAll.convention(false);
            binds = objectFactory.mapProperty(String.class, String.class);
            tmpFs = objectFactory.mapProperty(String.class, String.class);
            extraHosts = objectFactory.listProperty(String.class);
            logConfig = objectFactory.property(LogConfig.class);
            privileged = objectFactory.property(Boolean.class);
            privileged.convention(false);
            restartPolicy = objectFactory.property(String.class);
            capAdd = objectFactory.listProperty(String.class);
            capDrop = objectFactory.listProperty(String.class);
            devices = objectFactory.listProperty(String.class);
            shmSize = objectFactory.property(Long.class);
            autoRemove = objectFactory.property(Boolean.class);
            ipcMode = objectFactory.property(String.class);
            sysctls = objectFactory.mapProperty(String.class, String.class);
        }

        public void logConfig(String type, Map<String, String> config) {
            LogConfig logConfig = new LogConfig();
            logConfig.setType(type);
            logConfig.setConfig(config);
            this.logConfig.set(logConfig);
        }

        public void restartPolicy(final String name, final int maximumRetryCount) {
            this.restartPolicy.set(name + ":" + maximumRetryCount);
        }

        public static class LogConfig implements Serializable {
            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public Map<String, String> getConfig() {
                return config;
            }

            public void setConfig(Map<String, String> config) {
                this.config = config;
            }

            private String type;
            private Map<String, String> config = new HashMap<>();
        }
    }

    /**
     * @since 6.7.0
     */
    public static class HealthCheckConfig {
        /**
         * The time to wait between checks in nanoseconds. It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        public final Property<Long> getInterval() {
            return interval;
        }

        private final Property<Long> interval;

        /**
         * The time to wait before considering the check to have hung. It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        public final Property<Long> getTimeout() {
            return timeout;
        }

        private final Property<Long> timeout;

        @Input
        @Optional
        public final ListProperty<String> getCmd() {
            return cmd;
        }

        private final ListProperty<String> cmd;

        /**
         * The number of consecutive failures needed to consider a container as unhealthy. 0 means inherit.
         */
        @Input
        @Optional
        public final Property<Integer> getRetries() {
            return retries;
        }

        private final Property<Integer> retries;

        /**
         * The time to wait for container initialization before starting health-retries countdown in nanoseconds.
         * It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        public final Property<Long> getStartPeriod() {
            return startPeriod;
        }

        private final Property<Long> startPeriod;

        @Inject
        public HealthCheckConfig(ObjectFactory objectFactory) {
            interval = objectFactory.property(Long.class);
            timeout = objectFactory.property(Long.class);
            cmd = objectFactory.listProperty(String.class);
            retries = objectFactory.property(Integer.class);
            startPeriod = objectFactory.property(Long.class);
        }

        public void cmd(String shellCommand) {
            cmd.set(List.of(shellCommand));
        }
    }
}
