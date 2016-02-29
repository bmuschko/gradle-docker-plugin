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
package com.bmuschko.gradle.docker.utils

import groovy.transform.PackageScope
import org.gradle.api.GradleException

/**
 * This class is not thread-safe e.g. when the build is executed with {@code --parallel}.
 */
class TaskStateHelper {

    private final File stateFile

    public TaskStateHelper(String taskClassName, File buildDir) {
        this.stateFile = determineStateFile(taskClassName, buildDir)
    }

    private File determineStateFile(String taskClassName, File buildDir) {
        File stateDir = new File("${buildDir.path}/docker/state")
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new GradleException("Could not create state directory at ${stateDir.path}")
        }
        new File(stateDir, taskClassName)
    }

    void put(Object key, Object value) {
        saveState(key.toString(), value.toString())
    }

    String get(String key) {
        loadState()[key]
    }

    private Properties loadState() {
        Properties props = new Properties()
        if(stateFile.exists()) {
            props.load(stateFile.newInputStream())
        }
        props
    }

    private void saveState(String key, String value) {
        Properties props = loadState()
        props.setProperty(key, value)
        props.store(stateFile.newOutputStream(), null)
    }

    @PackageScope
    File getStateFile() {
        stateFile
    }
}