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

/**
 * Passes the underlying `docker-java` client to the defined `onNext` closure if it exists.
 */
public class DockerOperation extends AbstractDockerRemoteApiTask {
    @Override
    public void runRemoteCommand() {
        if (getNextHandler() != null) {
            getNextHandler().execute(getDockerClient());
        } else {
            getLogger().quiet("Execution amounts to a no-op if the onNext closure/reactive-stream is not defined.");
        }
    }
}
