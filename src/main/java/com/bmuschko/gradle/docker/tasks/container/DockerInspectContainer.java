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

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.VolumeBind;
import com.github.dockerjava.api.model.VolumesFrom;
import org.gradle.api.Action;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerInspectContainer extends DockerExistingContainer {

    public DockerInspectContainer() {
        defaultResponseHandling();
    }

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Inspecting container with ID '" + getContainerId().get() + "'.");
        InspectContainerResponse container = getDockerClient().inspectContainerCmd(getContainerId().get()).exec();

        if (getNextHandler() != null) {
            getNextHandler().execute(container);
        }
    }

    private void defaultResponseHandling() {
        Action<InspectContainerResponse> action = new Action<InspectContainerResponse>() {
            @Override
            public void execute(InspectContainerResponse container) {
                getLogger().quiet("Image ID    : " + container.getImageId());
                getLogger().quiet("Name        : " + container.getName());
                getLogger().quiet("Links       : " + Arrays.toString(container.getHostConfig().getLinks()));
                VolumeBind[] volumes = container.getVolumes() != null ? container.getVolumes() : new VolumeBind[0];
                getLogger().quiet("Volumes     : " + Arrays.toString(volumes));
                VolumesFrom[] volumesFrom = container.getHostConfig().getVolumesFrom() != null ? container.getHostConfig().getVolumesFrom() : new VolumesFrom[0];
                getLogger().quiet("VolumesFrom : " + Arrays.toString(volumesFrom));
                ExposedPort[] exposedPorts = container.getConfig().getExposedPorts() != null ? container.getConfig().getExposedPorts() : new ExposedPort[0];
                getLogger().quiet("ExposedPorts : " + Arrays.toString(exposedPorts));
                getLogger().quiet("LogConfig : " + container.getHostConfig().getLogConfig().getType().getType());
                getLogger().quiet("RestartPolicy : " + container.getHostConfig().getRestartPolicy());
                getLogger().quiet("PidMode : " + container.getHostConfig().getPidMode());
                List<String> devices = (container.getHostConfig().getDevices() != null ? Arrays.stream(container.getHostConfig().getDevices()) : Stream.<Device>empty())
                        .map(it -> it.getPathOnHost() + ":" + it.getPathInContainer() + ":" + it.getcGroupPermissions())
                        .collect(Collectors.toList());
                getLogger().quiet("Devices : " + devices);
                getLogger().quiet("TmpFs : " + container.getHostConfig().getTmpFs());
            }
        };

        onNext(action);
    }
}
