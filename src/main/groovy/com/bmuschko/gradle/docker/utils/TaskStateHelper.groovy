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

import org.gradle.api.Project

class TaskStateHelper {

    String taskName

    Project project

    public TaskStateHelper(String taskName, Project project) {
        this.taskName = taskName
        this.project = project
    }

    public void put(String key, String value) {
        put([ "${key}" : value])
    }

    public void put(Map<String, String> newState) {
        def state = get()
        newState.each { k, v -> state[k] = v}
        saveState(state)
    }

    public String get(String key) {
        get()[key]
    }

    public Map<String, String> get() {
        def map = [:]
        def props = loadStateProps()
        def names = props.propertyNames()

        while (names.hasMoreElements()) {
          def propName = names.nextElement()
          map[propName] = props.getProperty(propName)
        }
        map
    }

    Properties loadStateProps() {
        def stateFile = getStateFile()
        def props = new Properties()
        if(stateFile.exists()) {
          stateFile.withInputStream {
              props.load(it)
          }
        }
        props
    }

    File getStateFile() {
        def stateDir = "${project.buildDir}/docker/state"
        new File(stateDir).mkdirs()
        new File("${stateDir}/${taskName}")
    }

    void saveState(Map<String, String> state) {
        def props = new Properties()
        state.each { k, v -> props.setProperty(k, v) }
        getStateFile().withOutputStream {
            props.store(it, null)
        }
    }
}
