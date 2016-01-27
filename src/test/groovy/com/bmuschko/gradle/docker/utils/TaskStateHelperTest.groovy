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
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=testVal")
    }

    def "Saving task state doesn't overwrite previous state"() {
        project.buildDir >> createTempDir()
        def stateHelper = new TaskStateHelper("saveStateOverwrite", project)

        when:
        stateHelper.put("testKey", "testVal")
        stateHelper.put("testKey2", "testVal2")

        then:
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=testVal")
        stateHelper.getStateFile().text.contains("testKey2=testVal2")
    }

    def "Can overwrite state value for a given key"() {
        project.buildDir >> createTempDir()
        def stateHelper = new TaskStateHelper("stateOverwriteTest", project)

        when:
        stateHelper.put("testKey", "testVal")
        stateHelper.put("testKey", "secondTestVal")

        then:
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=secondTestVal")
    }

    def "Gets previously saved task state"() {
        project.buildDir >> createTempDir()
        def stateHelper = new TaskStateHelper("testStateTest", project)
        new File("${project.buildDir}/docker/state/").mkdirs()
        stateHelper.getStateFile().write("testKeyForGet=1234567")

        when:
        def val = stateHelper.get("testKeyForGet")

        then:
        val == "1234567"
    }

    def "Write/read integration"() {
      project.buildDir >> createTempDir()
      def stateHelper = new TaskStateHelper("writeReadIntg", project)

      when:
      stateHelper.put("gradlewwwww", "TEST_VALUE_xyz-123")
      def newHelper = new TaskStateHelper("writeReadIntg", project)
      def result = newHelper.get("gradlewwwww")

      then:
      result == "TEST_VALUE_xyz-123"
    }

    File createTempDir() {
        def temp = File.createTempFile("TaskStateHelperTest", Long.toString(System.nanoTime()))
        temp.delete()
        temp.mkdir()
        temp
    }
}
