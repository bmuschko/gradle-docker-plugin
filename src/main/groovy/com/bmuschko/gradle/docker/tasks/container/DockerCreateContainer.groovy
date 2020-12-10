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
package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.tasks.image.DockerExistingImage
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Device
import com.github.dockerjava.api.model.HealthCheck
import com.github.dockerjava.api.model.InternetProtocol
import com.github.dockerjava.api.model.Link
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.github.dockerjava.api.model.RestartPolicy
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.VolumesFrom
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

import javax.inject.Inject

class DockerCreateContainer extends DockerExistingImage {
    @Input
    @Optional
    final Property<String> containerName = project.objects.property(String)

    @Input
    @Optional
    final Property<String> hostName = project.objects.property(String)

    @Input
    @Optional
    final Property<String> ipv4Address = project.objects.property(String)

    @Input
    @Optional
    final ListProperty<String> portSpecs = project.objects.listProperty(String)

    @Input
    @Optional
    final Property<String> user = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> stdinOpen = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> stdinOnce = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> attachStdin = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> attachStdout = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> attachStderr = project.objects.property(Boolean)

    @Input
    @Optional
    final MapProperty<String, String> envVars = project.objects.mapProperty(String, String)

    @Input
    @Optional
    final ListProperty<String> cmd = project.objects.listProperty(String)

    @Input
    @Optional
    final ListProperty<String> entrypoint = project.objects.listProperty(String)

    @Input
    @Optional
    final ListProperty<String> networkAliases = project.objects.listProperty(String)

    @Input
    @Optional
    final Property<String> image = project.objects.property(String)

    @Input
    @Optional
    final ListProperty<String> volumes = project.objects.listProperty(String)

    @Input
    @Optional
    final Property<String> workingDir = project.objects.property(String)

    @Input
    final ListProperty<ExposedPort> exposedPorts = project.objects.listProperty(ExposedPort)

    @Input
    @Optional
    final Property<Boolean> tty = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<String> pid = project.objects.property(String)

    @Input
    @Optional
    final MapProperty<String, String> labels = project.objects.mapProperty(String, String)

    @Internal
    final Property<String> containerId = project.objects.property(String)

    @Input
    @Optional
    final Property<String> macAddress = project.objects.property(String)

    @Nested
    final HostConfig hostConfig

    @Nested
    final HealthCheckConfig healthCheck

    @Inject
    DockerCreateContainer(ObjectFactory objectFactory) {
        hostConfig = objectFactory.newInstance(HostConfig, objectFactory)
        healthCheck = objectFactory.newInstance(HealthCheckConfig, objectFactory)
        portSpecs.empty()
        stdinOpen.set(false)
        stdinOnce.set(false)
        attachStdin.set(false)
        attachStdout.set(false)
        attachStderr.set(false)
        cmd.empty()
        entrypoint.empty()
        networkAliases.empty()
        volumes.empty()
        exposedPorts.empty()
        tty.set(false)
    }

    @Override
    void runRemoteCommand() {
        CreateContainerCmd containerCommand = dockerClient.createContainerCmd(imageId.get())
        setContainerCommandConfig(containerCommand)
        CreateContainerResponse container = containerCommand.exec()
        final String localContainerName = containerName.getOrNull() ?: container.id
        logger.quiet "Created container with ID '$localContainerName'."
        containerId.set(container.id)
        if(nextHandler) {
            nextHandler.execute(container)
        }
    }

    void exposePorts(String internetProtocol, List<Integer> ports) {
        exposedPorts.add(new ExposedPort(internetProtocol, ports))
    }

    void withEnvVar(String key, String value) {
        if (envVars.getOrNull()) {
            envVars.put(key, value)
        } else {
            envVars.set([(key): value])
        }
    }

    private static HealthCheck getOrCreateHealthCheck(CreateContainerCmd containerCommand) {
        if (containerCommand.healthcheck == null) {
            containerCommand.withHealthcheck(new HealthCheck())
        }
        return containerCommand.healthcheck
    }

    private void setContainerCommandConfig(CreateContainerCmd containerCommand) {
        if(containerName.getOrNull()) {
            containerCommand.withName(containerName.get())
        }

        if(hostName.getOrNull()) {
            containerCommand.withHostName(hostName.get())
        }

        if(ipv4Address.getOrNull()){
            containerCommand.withIpv4Address(ipv4Address.get())
        }

        if(portSpecs.getOrNull()) {
            containerCommand.withPortSpecs(portSpecs.get())
        }

        if(user.getOrNull()) {
            containerCommand.withUser(user.get())
        }

        if(hostConfig.groups.getOrNull()) {
            containerCommand.hostConfig.withGroupAdd(hostConfig.groups.get())
        }

        if(stdinOpen.getOrNull()) {
            containerCommand.withStdinOpen(stdinOpen.get())
        }

        if(stdinOnce.getOrNull()) {
            containerCommand.withStdInOnce(stdinOnce.get())
        }

        if(hostConfig.memory.getOrNull()) {
            containerCommand.hostConfig.withMemory(hostConfig.memory.get())
        }

        if(hostConfig.memorySwap.getOrNull()) {
            containerCommand.hostConfig.withMemorySwap(hostConfig.memorySwap.get())
        }

        if(hostConfig.cpuset.getOrNull()) {
            containerCommand.hostConfig.withCpusetCpus(hostConfig.cpuset.get())
        }

        if(attachStdin.getOrNull()) {
            containerCommand.withAttachStdin(attachStdin.get())
        }

        if(attachStdout.getOrNull()) {
            containerCommand.withAttachStdout(attachStdout.get())
        }

        if(attachStderr.getOrNull()) {
            containerCommand.withAttachStderr(attachStderr.get())
        }

        // marshall map into list
        if(envVars.getOrNull()) {
            containerCommand.withEnv(envVars.get().collect { key, value -> "${key}=${value}".toString() })
        }

        if(cmd.getOrNull()) {
            containerCommand.withCmd(cmd.get())
        }

        if(entrypoint.getOrNull()) {
            containerCommand.withEntrypoint(entrypoint.get())
        }

        if(hostConfig.dns.getOrNull()) {
            containerCommand.hostConfig.withDns(hostConfig.dns.get())
        }

        if(hostConfig.network.getOrNull()) {
            containerCommand.hostConfig.withNetworkMode(hostConfig.network.get())
        }

        if(networkAliases.getOrNull()) {
            containerCommand.withAliases(networkAliases.get())
        }

        if(image.getOrNull()) {
            containerCommand.withImage(image.get())
        }

        if(volumes.getOrNull()) {
            List<Volume> createdVolumes = volumes.get().collect { Volume.parse(it) }
            containerCommand.withVolumes(createdVolumes)
        }

        if (hostConfig.links.getOrNull()) {
            List<Link> createdLinks = hostConfig.links.get().collect { Link.parse(it) }
            containerCommand.hostConfig.withLinks(createdLinks as Link[])
        }

        if(hostConfig.volumesFrom.getOrNull()) {
            List<VolumesFrom> createdVolumes = hostConfig.volumesFrom.get().collect { new VolumesFrom(it) }
            containerCommand.hostConfig.withVolumesFrom(createdVolumes)
        }

        if(workingDir.getOrNull()) {
            containerCommand.withWorkingDir(workingDir.get())
        }

        if(exposedPorts.getOrNull()) {
            List<List<com.github.dockerjava.api.model.ExposedPort>> allPorts = exposedPorts.get().collect { exposedPort ->
                exposedPort.ports.collect {
                    Integer port -> new com.github.dockerjava.api.model.ExposedPort(port, InternetProtocol.parse(exposedPort.internetProtocol.toLowerCase()))
                }
            }
            containerCommand.withExposedPorts(allPorts.flatten() as List<com.github.dockerjava.api.model.ExposedPort>)
        }

        if(hostConfig.portBindings.getOrNull()) {
            List<PortBinding> createdPortBindings = hostConfig.portBindings.get().collect { PortBinding.parse(it) }
            containerCommand.hostConfig.withPortBindings(new Ports(createdPortBindings as PortBinding[]))
        }

        if(hostConfig.publishAll.getOrNull()) {
            containerCommand.hostConfig.withPublishAllPorts(hostConfig.publishAll.get())
        }

        if(hostConfig.binds.getOrNull()) {
            List<Bind> createdBinds = hostConfig.binds.get().collect { Bind.parse([it.key, it.value].join(':')) }
            containerCommand.hostConfig.withBinds(createdBinds)
        }

        if(hostConfig.extraHosts.getOrNull()) {
            containerCommand.hostConfig.withExtraHosts(hostConfig.extraHosts.get() as String[])
        }

        if(hostConfig.logConfig.getOrNull()) {
            com.github.dockerjava.api.model.LogConfig.LoggingType type = com.github.dockerjava.api.model.LogConfig.LoggingType.fromValue(hostConfig.logConfig.get().type)
            com.github.dockerjava.api.model.LogConfig config = new com.github.dockerjava.api.model.LogConfig(type, hostConfig.logConfig.get().config)
            containerCommand.hostConfig.withLogConfig(config)
        }

        if(hostConfig.privileged.getOrNull()) {
            containerCommand.hostConfig.withPrivileged(hostConfig.privileged.get())
        }

        if (hostConfig.restartPolicy.getOrNull()) {
            containerCommand.hostConfig.withRestartPolicy(RestartPolicy.parse(hostConfig.restartPolicy.get()))
        }

        if (pid.getOrNull()) {
            containerCommand.getHostConfig().withPidMode(pid.get())
        }

        if (hostConfig.devices.getOrNull()) {
            List<Device> createdDevices = hostConfig.devices.get().collect { Device.parse(it) }
            containerCommand.hostConfig.withDevices(createdDevices)
        }

        if(tty.getOrNull()) {
            containerCommand.withTty(tty.get())
        }

        if(hostConfig.shmSize.getOrNull() != null) { // 0 is valid input
            containerCommand.hostConfig.withShmSize(hostConfig.shmSize.get())
        }

        if (hostConfig.autoRemove.getOrNull()) {
            containerCommand.hostConfig.withAutoRemove(hostConfig.autoRemove.get())
        }

        if(labels.getOrNull()) {
            containerCommand.withLabels(labels.get())
        }

        if(macAddress.getOrNull()) {
            containerCommand.withMacAddress(macAddress.get())
        }

        if(hostConfig.ipcMode.getOrNull()) {
            containerCommand.hostConfig.withIpcMode(hostConfig.ipcMode.get())
        }

        if(hostConfig.sysctls.getOrNull()) {
            containerCommand.hostConfig.withSysctls(hostConfig.sysctls.get())
        }

        if (healthCheck.interval.getOrNull()) {
            getOrCreateHealthCheck(containerCommand).withInterval(healthCheck.interval.get())
        }

        if (healthCheck.timeout.getOrNull()) {
            getOrCreateHealthCheck(containerCommand).withTimeout(healthCheck.timeout.get())
        }

        if (healthCheck.cmd.getOrNull()) {
            String command = healthCheck.cmd.get().size() == 1 ? 'CMD-SHELL' : 'CMD'
            List<String> test = [command] + healthCheck.cmd.get()
            getOrCreateHealthCheck(containerCommand).withTest(test)
        }

        if (healthCheck.retries.getOrNull()) {
            getOrCreateHealthCheck(containerCommand).withRetries(healthCheck.retries.get())
        }

        if (healthCheck.startPeriod.getOrNull()) {
            getOrCreateHealthCheck(containerCommand).withStartPeriod(healthCheck.startPeriod.get())
        }
    }

    static class ExposedPort {
        final String internetProtocol
        final List<Integer> ports

        ExposedPort(String internetProtocol, List<Integer> ports) {
            this.internetProtocol = internetProtocol
            this.ports = ports
        }
    }

    /**
     * @since 6.0.0
     */
    static class HostConfig {
        /**
         * A list of additional groups that the container process will run as.
         */
        @Input
        @Optional
        final ListProperty<String> groups

        @Input
        @Optional
        final Property<Long> memory

        @Input
        @Optional
        final Property<Long> memorySwap

        @Input
        @Optional
        final Property<String> cpuset

        @Input
        @Optional
        final ListProperty<String> dns

        @Input
        @Optional
        final Property<String> network

        @Input
        @Optional
        final ListProperty<String> links

        @Input
        @Optional
        final ListProperty<String> volumesFrom

        @Input
        @Optional
        final ListProperty<String> portBindings

        @Input
        @Optional
        final Property<Boolean> publishAll

        @Input
        @Optional
        final MapProperty<String, String> binds

        @Input
        @Optional
        final ListProperty<String> extraHosts

        @Input
        @Optional
        final Property<LogConfig> logConfig

        @Input
        @Optional
        final Property<Boolean> privileged

        @Input
        @Optional
        final Property<String> restartPolicy

        @Input
        @Optional
        final ListProperty<String> devices

        /**
         * Size of {@code /dev/shm} in bytes.
         * <p>
         * The size must be greater than 0. If omitted the system uses 64MB.
         */
        @Input
        @Optional
        final Property<Long> shmSize

        /**
         * Automatically remove the container when the container's process exits.
         * <p>
         * This has no effect if {@link #restartPolicy} is set.
         */
        @Input
        @Optional
        final Property<Boolean> autoRemove

        /**
         * The IPC mode for the container.
         * <ol>
         * <li>{@code none} - Own private IPC namespace, with /dev/shm not mounted.</li>
         * <li>{@code private} - Own private IPC namespace.</li>
         * <li>{@code shareable" - Own private IPC namespace, with a possibility to share it with other containers.</li>
         * <li>{@code container <_name-or-ID_>} - Join another ("shareable") container’s IPC namespace.</li>
         * <li>{@code host} - Use the host system’s IPC namespace.</li>
         * </ol>
         */
        @Input
        @Optional
        final Property<String> ipcMode

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
        final MapProperty<String, String> sysctls

        @Inject
        HostConfig(ObjectFactory objectFactory) {
            groups = objectFactory.listProperty(String)
            groups.empty()
            memory = objectFactory.property(Long)
            memorySwap = objectFactory.property(Long)
            cpuset = objectFactory.property(String)
            dns = objectFactory.listProperty(String)
            dns.empty()
            network = objectFactory.property(String)
            links = objectFactory.listProperty(String)
            links.empty()
            volumesFrom = objectFactory.listProperty(String)
            volumesFrom.empty()
            portBindings = objectFactory.listProperty(String)
            portBindings.empty()
            publishAll = objectFactory.property(Boolean)
            publishAll.set(false)
            binds = objectFactory.mapProperty(String, String)
            extraHosts = objectFactory.listProperty(String)
            extraHosts.empty()
            logConfig = objectFactory.property(LogConfig)
            privileged = objectFactory.property(Boolean)
            privileged.set(false)
            restartPolicy = objectFactory.property(String)
            devices = objectFactory.listProperty(String)
            shmSize = objectFactory.property(Long)
            autoRemove = objectFactory.property(Boolean)
            autoRemove.set(false)
            ipcMode = objectFactory.property(String)
            sysctls = objectFactory.mapProperty(String, String)
        }

        void logConfig(String type, Map<String, String> config) {
            this.logConfig.set(new LogConfig(type: type, config: config))
        }

        void restartPolicy(String name, int maximumRetryCount) {
            this.restartPolicy.set("${name}:${maximumRetryCount}".toString())
        }

        static class LogConfig {
            String type
            Map<String, String> config = [:]
        }
    }

    static class HealthCheckConfig {
        /**
         * The time to wait between checks in nanoseconds. It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        final Property<Long> interval

        /**
         * The time to wait before considering the check to have hung. It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        final Property<Long> timeout

        @Input
        @Optional
        final ListProperty<String> cmd

        /**
         * The number of consecutive failures needed to consider a container as unhealthy. 0 means inherit.
         */
        @Input
        @Optional
        final Property<Integer> retries

        /**
         * The time to wait for container initialization before starting health-retries countdown in nanoseconds.
         * It should be 0 or at least 1000000 (1 ms). 0 means inherit.
         */
        @Input
        @Optional
        final Property<Long> startPeriod

        @Inject
        HealthCheckConfig(ObjectFactory objectFactory) {
            interval = objectFactory.property(Long)
            timeout = objectFactory.property(Long)
            cmd = objectFactory.listProperty(String)
            cmd.empty()
            retries = objectFactory.property(Integer)
            startPeriod = objectFactory.property(Long)
        }

        void cmd(String shellCommand) {
            cmd.set([shellCommand])
        }
    }
}
