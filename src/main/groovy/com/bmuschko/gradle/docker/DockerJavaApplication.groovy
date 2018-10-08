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
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested

@CompileStatic
class DockerJavaApplication {
    final Property<String> baseImage
    final Property<String> maintainer
    final ListProperty<Integer> ports
    final Property<String> tag

    private final CompositeExecInstruction compositeExecInstruction

    DockerJavaApplication(Project project) {
        baseImage = project.objects.property(String)
        baseImage.set('openjdk:jre-alpine')
        maintainer = project.objects.property(String)
        maintainer.set(System.getProperty('user.name'))
        ports = project.objects.listProperty(Integer)
        ports.add(8080)
        tag = project.objects.property(String)
        compositeExecInstruction = new CompositeExecInstruction(project)
    }

    CompositeExecInstruction exec(Action<CompositeExecInstruction> action) {
        compositeExecInstruction.clear()
        action.execute(compositeExecInstruction)
        return compositeExecInstruction
    }

    CompositeExecInstruction getExecInstruction() {
        compositeExecInstruction
    }

    /**
     * Helper Instruction to allow customizing generated ENTRYPOINT/CMD.
     */
    static class CompositeExecInstruction implements Dockerfile.Instruction {
        private final ListProperty<Dockerfile.Instruction> instructions

        CompositeExecInstruction(Project project) {
            instructions = project.objects.listProperty(Dockerfile.Instruction)
        }

        @Nested
        ListProperty<Dockerfile.Instruction> getInstructions() {
            instructions
        }

        @Override
        String getKeyword() { '' }

        void clear() {
            instructions.set([])
        }

        @Override
        @CompileStatic(TypeCheckingMode.SKIP)
        String getText() {
            List<Dockerfile.Instruction> viableInstructions = instructions.get().findAll { it.text }
            viableInstructions.collect { it.getText() }.join(System.getProperty('line.separator'))
        }

        void defaultCommand(String... command) {
            addInstruction(new Dockerfile.DefaultCommandInstruction(command))
        }

        void defaultCommand(Provider<List<String>> commandProvider) {
            addInstruction(new Dockerfile.DefaultCommandInstruction(commandProvider))
        }

        void entryPoint(String... entryPoint) {
            addInstruction(new Dockerfile.EntryPointInstruction(entryPoint))
        }

        void entryPoint(Provider<List<String>> entryPointProvider) {
            addInstruction(new Dockerfile.EntryPointInstruction(entryPointProvider))
        }

        private void addInstruction(Dockerfile.Instruction instruction) {
            instructions.add(instruction)
        }
    }
}
