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

import org.gradle.api.GradleException

class TaskStateHelper {

    String taskName

    File buildDir

    public TaskStateHelper(String taskName, File buildDir) {
        this.taskName = taskName
        this.buildDir = buildDir
    }

    public void put(Object key, Object value) {
        saveState(key.toString(), value.toString())
    }

    public String get(String key) {
        loadState()[key]
    }

    File getStateFile() {
        File stateDir = new File("${buildDir.path}/docker/state")
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            throw new GradleException("Could not create state directory at ${stateDir.path}")
        }
        new File(stateDir, taskName)
    }

    Properties loadState() {
        File stateFile = getStateFile()
        Properties props = new Properties()
        if(stateFile.exists()) {
            props.load(stateFile.newInputStream())
        }
        props
    }

    void saveState(String key, String value) {
        Properties props = loadState()
        props.setProperty(key, value)
        props.store(getStateFile().newOutputStream(), null)
    }
}