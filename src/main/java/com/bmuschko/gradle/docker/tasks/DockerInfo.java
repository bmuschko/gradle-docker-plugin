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
package com.bmuschko.gradle.docker.tasks;

import com.github.dockerjava.api.model.Info;

import java.util.Arrays;

public class DockerInfo extends AbstractDockerRemoteApiTask {

    @Override
    public void runRemoteCommand() {
        getLogger().quiet("Retrieving Docker info.");
        Info info = getDockerClient().infoCmd().exec();

        if (getNextHandler() != null) {
            getNextHandler().execute(info);
        } else {
            getLogger().quiet("Debug                : " + info.getDebug());
            getLogger().quiet("Containers           : " + info.getContainers());
            getLogger().quiet("Driver               : " + info.getDriver());
            getLogger().quiet("Driver Statuses      : " + info.getDriverStatuses());
            getLogger().quiet("Images               : " + info.getImages());
            getLogger().quiet("IPv4 Forwarding      : " + info.getIPv4Forwarding());
            getLogger().quiet("Index Server Address : " + info.getIndexServerAddress());
            getLogger().quiet("Init Path            : " + info.getInitPath());
            getLogger().quiet("Init SHA1            : " + info.getInitSha1());
            getLogger().quiet("Kernel Version       : " + info.getKernelVersion());
            getLogger().quiet("Sockets              : " + Arrays.toString(info.getSockets()));
            getLogger().quiet("Memory Limit         : " + info.getMemoryLimit());
            getLogger().quiet("nEvent Listener      : " + info.getNEventsListener());
            getLogger().quiet("NFd                  : " + info.getNFd());
            getLogger().quiet("NGoroutines          : " + info.getNGoroutines());
            getLogger().quiet("Swap Limit           : " + info.getSwapLimit());
            getLogger().quiet("Execution Driver     : " + info.getExecutionDriver());
        }
    }
}
