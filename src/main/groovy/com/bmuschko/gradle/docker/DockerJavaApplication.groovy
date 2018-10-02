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
import org.gradle.api.Action
import org.gradle.api.tasks.Nested

class DockerJavaApplication {
    String baseImage = 'openjdk:jre-alpine'
    private final CompositeExecInstruction compositeExecInstruction = new CompositeExecInstruction()

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

    CompositeExecInstruction exec(Action<CompositeExecInstruction> action) {
        compositeExecInstruction.clear()
        action.execute(compositeExecInstruction)
    }

    CompositeExecInstruction getExecInstruction() {
        compositeExecInstruction
    }

    /**
     * Helper Instruction to allow customizing generated ENTRYPOINT/CMD.
     */
    static class CompositeExecInstruction implements Dockerfile.Instruction {
        private final List<Dockerfile.Instruction> instructions = new ArrayList<Dockerfile.Instruction>()

        @Nested
        List<Dockerfile.Instruction> getInstructions() {
            instructions
        }

        @Override
        String getKeyword() { '' }

        @Override
        String getText() {
            build()
        }

        @Override
        String build() { instructions*.getText().join(System.getProperty('line.separator')) }

        void clear() {
            instructions.clear()
        }

        void defaultCommand(String... command) {
            instructions << new Dockerfile.DefaultCommandInstruction(command)
        }

        void defaultCommand(Closure command) {
            instructions << new Dockerfile.DefaultCommandInstruction(command)
        }

        void entryPoint(String... entryPoint) {
            instructions << new Dockerfile.EntryPointInstruction(entryPoint)
        }

        void entryPoint(Closure entryPoint) {
            instructions << new Dockerfile.EntryPointInstruction(entryPoint)
        }
    }
}
