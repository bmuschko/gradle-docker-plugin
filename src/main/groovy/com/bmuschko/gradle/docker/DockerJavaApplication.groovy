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
package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile.CompositeExecInstruction

class DockerJavaApplication {
    String baseImage = 'openjdk:jre-alpine'
    final CompositeExecInstruction exec = new CompositeExecInstruction()

    /**
     * Deprecated as per https://docs.docker.com/engine/deprecated/#maintainer-in-dockerfile
     * Will be removed in 4.x release.
     */
    @Deprecated
    String maintainer = System.getProperty('user.name')

    /**
     * Temporary solution to be able to create image without
     * <code>MAINTAINER</code> and preserve backward compatibility.
     * Will be removed in 4.x release.
     */
    boolean skipMaintainer

    @Deprecated
    Integer port = 8080
    Set<Integer> ports
    String tag

    Integer[] getPorts() {
        return ports != null ? ports : [port]
    }

    CompositeExecInstruction exec(@DelegatesTo(CompositeExecInstruction) Closure<Void> closure) {
        exec.clear()
        exec.apply(closure)
    }
}
