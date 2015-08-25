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
import spock.lang.Specification

class TaskStateHelperTest extends Specification {

    def project = Mock(Project)

    def "Saves task state"() {
        project.buildDir >> createTempDir()
        def stateHelper = new TaskStateHelper("saveStateTest", project)

        when:
        stateHelper.put("testKey", "testVal")

        then:
        new File("${project.buildDir}/docker/state/saveStateTest").exists()
        new File("${project.buildDir}/docker/state/saveStateTest").text.contains("testKey=testVal")
    }

    def "Gets previously saved task state"() {
        project.buildDir >> createTempDir()
        def stateHelper = new TaskStateHelper("testStateTest", project)
        new File("${project.buildDir}/docker/state/").mkdirs()
        new File("${project.buildDir}/docker/state/testStateTest").write("testKeyForGet=1234567")

        when:
        def val = stateHelper.get("testKeyForGet")

        then:
        val == "1234567"
    }

    File createTempDir() {
        def temp = File.createTempFile("TaskStateHelperTest", Long.toString(System.nanoTime()))
        temp.delete()
        temp.mkdir()
        temp
    }
}
