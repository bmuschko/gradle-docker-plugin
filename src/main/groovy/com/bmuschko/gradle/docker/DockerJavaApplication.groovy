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

import com.bmuschko.gradle.docker.tasks.image.Dockerfile

class DockerJavaApplication {
    String baseImage = 'java'
    Closure<Void> exec
    String maintainer = System.getProperty('user.name')
    @Deprecated
    Integer port = 8080
    Set<Integer> ports = []
    String tag

    Integer[] getPorts() {
        return ports.size() > 0 ? ports : [port]
    }

    void exec(@DelegatesTo(Dockerfile) Closure<Void> closure) {
        this.exec = closure
    }
}
