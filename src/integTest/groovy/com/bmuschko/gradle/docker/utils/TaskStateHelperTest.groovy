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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class TaskStateHelperTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "Saves task state"() {
        def stateHelper = new TaskStateHelper("saveStateTest", temporaryFolder.root)

        when:
        stateHelper.put("testKey", "testVal")

        then:
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=testVal")
    }

    def "Saving task state doesn't overwrite previous state"() {
        def stateHelper = new TaskStateHelper("saveStateOverwrite", temporaryFolder.root)

        when:
        stateHelper.put("testKey", "testVal")
        stateHelper.put("testKey2", "testVal2")

        then:
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=testVal")
        stateHelper.getStateFile().text.contains("testKey2=testVal2")
    }

    def "Can overwrite state value for a given key"() {
        def stateHelper = new TaskStateHelper("stateOverwriteTest", temporaryFolder.root)

        when:
        stateHelper.put("testKey", "testVal")
        stateHelper.put("testKey", "secondTestVal")

        then:
        stateHelper.getStateFile().exists()
        stateHelper.getStateFile().text.contains("testKey=secondTestVal")
    }

    def "Gets previously saved task state"() {
        def stateHelper = new TaskStateHelper("testStateTest", temporaryFolder.root)
        new File("${temporaryFolder.root}/docker/state/").mkdirs()
        stateHelper.getStateFile().write("testKeyForGet=1234567")

        when:
        def val = stateHelper.get("testKeyForGet")

        then:
        val == "1234567"
    }

    def "Write/read integration"() {
        def stateHelper = new TaskStateHelper("writeReadIntg", temporaryFolder.root)

        when:
        stateHelper.put("gradlewwwww", "TEST_VALUE_xyz-123")
        def newHelper = new TaskStateHelper("writeReadIntg", temporaryFolder.root)
        def result = newHelper.get("gradlewwwww")

        then:
        result == "TEST_VALUE_xyz-123"
    }
}