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
package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.image.data.File
import com.bmuschko.gradle.docker.tasks.image.data.From
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*

@CacheableTask
class Dockerfile extends DefaultTask {
    private final ListProperty<Instruction> instructions

    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty destFile

    Dockerfile() {
        instructions = project.objects.listProperty(Instruction)
        destFile = newOutputFile()
        destFile.set(project.layout.buildDirectory.file('docker/Dockerfile'))
    }

    @Nested
    ListProperty<Instruction> getInstructions() {
        instructions
    }

    @TaskAction
    void create() {
        verifyValidInstructions()

        destFile.get().asFile.withWriter { out ->
            instructions.get().each { instruction ->
                String instructionText = instruction.getText()

                if (instructionText) {
                    out.println instructionText
                }
            }
        }
    }

    private void verifyValidInstructions() {
        if (instructions.get().empty) {
            throw new IllegalStateException('Please specify instructions for your Dockerfile')
        }

        def fromPos = instructions.get().findIndexOf { it.keyword == 'FROM' }
        def othersPos = instructions.get().findIndexOf { it.keyword != 'ARG' && it.keyword != 'FROM' }
        if (fromPos < 0 || (othersPos >= 0 && fromPos > othersPos)) {
            throw new IllegalStateException('The first instruction of a Dockerfile has to be FROM (or ARG for Docker later than 17.05)')
        }
    }

    void instructionsFromTemplate(java.io.File template) {
        if (!template.exists()) {
            throw new FileNotFoundException("docker template file not found at location : ${template.getAbsolutePath()}")
        }
        template.readLines().findAll { it.length() > 0 } each { String instruction ->
            instructions.add(new GenericInstruction(instruction))
        }
    }

    void instructionsFromTemplate(String templatePath) {
        instructionsFromTemplate(project.file(templatePath))
    }

    /**
     * Adds a full instruction as String.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     instruction('FROM ubuntu:14.04')
     *     instruction('LABEL maintainer=benjamin.muschko@gmail.com')
     * }
     * </pre>
     *
     * @param instruction Instruction as String
     * @see #instruction(Provider)
     */
    void instruction(String instruction) {
        instructions.add(new GenericInstruction(instruction))
    }

    /**
     * Adds a full instruction as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     instruction(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             'FROM ubuntu:14.04'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Instruction as Provider
     * @see #instruction(String)
     * @since 4.0
     */
    void instruction(Provider<String> provider) {
        instructions.add(new GenericInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#from">FROM instruction</a> sets the Base Image for
     * subsequent instructions.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     from('ubuntu:14.04')
     * }
     * </pre>
     *
     * @param image Base image name
     * @param stageName stage name in case of multi-stage builds (default null)
     * @see #from(Provider)
     */
    void from(String image, String stageName = null) {
        instructions.add(new FromInstruction(image, stageName))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#from">FROM instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * import com.bmuschko.gradle.docker.tasks.image.data.From
     *
     * task createDockerfile(type: Dockerfile) {
     *     from(project.provider(new Callable<From>() {
     *         @Override
     *         From call() throws Exception {
     *             new From('ubuntu:14.04')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider From information as Provider
     * @see #from(String, String)
     * @since 4.0
     */
    void from(Provider<From> provider) {
        instructions.add(new FromInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#arg">ARG instruction</a> defines a variable that
     * users can pass at build-time to the builder.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     arg('user1=someuser')
     * }
     * </pre>
     *
     * @param arg Argument to pass, possibly with default value.
     * @see #arg(Provider)
     */
    void arg(String arg) {
        instructions.add(new ArgInstruction(arg))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#arg">ARG instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     arg(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             'user1=someuser'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Argument to pass as Provider
     * @see #arg(String)
     * @since 4.0
     */
    void arg(Provider<String> provider) {
        instructions.add(new ArgInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#run">RUN instruction</a> will execute any commands in a
     * new layer on top of the current image and commit the results.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     runCommand('/bin/bash -c echo hello')
     * }
     * </pre>
     *
     * @param command Command
     * @see #runCommand(Provider)
     */
    void runCommand(String command) {
        instructions.add(new RunCommandInstruction(command))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#run">RUN instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     runCommand(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             '/bin/bash -c echo hello'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Command as Provider
     * @see #runCommand(String)
     * @since 4.0
     */
    void runCommand(Provider<String> provider) {
        instructions.add(new RunCommandInstruction(provider))
    }

    /**
     * The main purpose of a <a href="https://docs.docker.com/engine/reference/builder/#cmd">CMD instruction</a> is to provide
     * defaults for an executing container.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     defaultCommand('/usr/bin/wc', '--help')
     * }
     * </pre>
     *
     * @param command Command
     * @see #defaultCommand(Provider)
     */
    void defaultCommand(String... command) {
        instructions.add(new DefaultCommandInstruction(command))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#cmd">CMD instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     defaultCommand(project.provider(new Callable<List<String>>() {
     *         @Override
     *         List<String> call() throws Exception {
     *             ['/usr/bin/wc', '--help']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Command as Provider
     * @see #defaultCommand(String...)
     * @since 4.0
     */
    void defaultCommand(Provider<List<String>> provider) {
        instructions.add(new DefaultCommandInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#expose">EXPOSE instruction</a> informs Docker that the
     * container will listen on the specified network ports at runtime.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     exposePort(8080, 9090)
     * }
     * </pre>
     *
     * @param ports Ports
     * @see #exposePort(Provider)
     */
    void exposePort(Integer... ports) {
        instructions.add(new ExposePortInstruction(ports))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#expose">EXPOSE instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     exposePort(project.provider(new Callable<List<Integer>>() {
     *         @Override
     *         List<Integer> call() throws Exception {
     *             [8080, 9090]
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param ports Ports as Provider
     * @see #exposePort(Integer...)
     * @since 4.0
     */
    void exposePort(Provider<List<Integer>> provider) {
        instructions.add(new ExposePortInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#env">ENV instruction</a> sets the environment variable
     * <key> to the value <value>. This value will be passed to all future RUN instructions.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     environmentVariable('myName', 'John Doe')
     * }
     * </pre>
     *
     * @param key Key
     * @param value Value
     * @see #environmentVariable(Map)
     * @see #environmentVariable(Provider)
     */
    void environmentVariable(String key, String value) {
        instructions.add(new EnvironmentVariableInstruction(key, value))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#env">ENV instruction</a> as Map.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     environmentVariable(['myName': 'John Doe'])
     * }
     * </pre>
     *
     * @param envVars Environment variables
     * @see #environmentVariable(String, String)
     * @see #environmentVariable(Provider)
     */
    void environmentVariable(Map<String, String> envVars) {
        instructions.add(new EnvironmentVariableInstruction(envVars))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#env">ENV instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     environmentVariable(project.provider(new Callable<Map<String, String>>() {
     *         @Override
     *         Map<String, String> call() throws Exception {
     *             ['myName': 'John Doe']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Environment variables as Provider
     * @see #environmentVariable(String, String)
     * @see #environmentVariable(Map)
     * @since 4.0
     */
    void environmentVariable(Provider<Map<String, String>> provider) {
        instructions.add(new EnvironmentVariableInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#add">ADD instruction</a> copies new files, directories
     * or remote file URLs from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     addFile('test', '/absoluteDir/')
     * }
     * </pre>
     *
     * @param src Source file
     * @param dest Destination path
     * @see #addFile(Provider)
     */
    void addFile(String src, String dest) {
        instructions.add(new AddFileInstruction(src, dest))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#add">ADD instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * import com.bmuschko.gradle.docker.tasks.image.data.File
     *
     * task createDockerfile(type: Dockerfile) {
     *     addFile(project.provider(new Callable<File>() {
     *         @Override
     *         File call() throws Exception {
     *             new File('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Add instruction as Provider
     * @see #addFile(String, String)
     * @since 4.0
     */
    void addFile(Provider<File> provider) {
        instructions.add(new AddFileInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#copy">COPY instruction</a> copies new files or directories
     * from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     copyFile('test', '/absoluteDir/')
     * }
     * </pre>
     *
     * @param src Source file
     * @param dest Destination path
     * @param stageName stage name in case of multi stage build
     * @see #copyFile(Provider)
     */
    void copyFile(String src, String dest, String stageName = null) {
        instructions.add(new CopyFileInstruction(src, dest, stageName))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#copy">COPY instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * import com.bmuschko.gradle.docker.tasks.image.data.File
     *
     * task createDockerfile(type: Dockerfile) {
     *     copyFile(project.provider(new Callable<File>() {
     *         @Override
     *         File call() throws Exception {
     *             new File('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Copy instruction as Provider
     * @see #copyFile(String, String, String)
     * @since 4.0
     */
    void copyFile(Provider<File> provider) {
        instructions.add(new CopyFileInstruction(provider))
    }

    /**
     * An <a href="https://docs.docker.com/engine/reference/builder/#entrypoint">ENTRYPOINT</a> allows you to configure a container
     * that will run as an executable.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     entryPoint('top', '-b')
     * }
     * </pre>
     *
     * @param entryPoint Entry point
     * @see #entryPoint(Provider)
     */
    void entryPoint(String... entryPoint) {
        instructions.add(new EntryPointInstruction(entryPoint))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#entrypoint">ENTRYPOINT</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     entryPoint(project.provider(new Callable<List<String>>() {
     *         @Override
     *         List<String> call() throws Exception {
     *             ['top', '-b']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param entryPoint Entry point
     * @see #entryPoint(String...)
     * @since 4.0
     */
    void entryPoint(Provider<List<String>> provider) {
        instructions.add(new EntryPointInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#volume">VOLUME instruction</a> will create a mount point
     * with the specified name and mark it as holding externally mounted volumes from native host or other containers.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     volume('/myvol')
     * }
     * </pre>
     *
     * @param volume Volume
     * @see #volume(Provider)
     */
    void volume(String... volume) {
        instructions.add(new VolumeInstruction(volume))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#volume">VOLUME instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     volume(project.provider(new Callable<List<String>>() {
     *         @Override
     *         List<String> call() throws Exception {
     *             ['/myvol']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param volume Volume
     * @see #volume(String...)
     * @since 4.0
     */
    void volume(Provider<List<String>> provider) {
        instructions.add(new VolumeInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#user">USER instruction</a> sets the user name or UID to
     * use when running the image and for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     user('patrick')
     * }
     * </pre>
     *
     * @param user User
     * @see #user(Provider)
     */
    void user(String user) {
        instructions.add(new UserInstruction(user))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#user">USER instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     user(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             'patrick'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider User as Provider
     * @see #user(String)
     * @since 4.0
     */
    void user(Provider<String> provider) {
        instructions.add(new UserInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#workdir">WORKDIR instruction</a> sets the working directory
     * for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     workingDir('/path/to/workdir')
     * }
     * </pre>
     *
     * @param dir Directory
     * @see #workingDir(Provider)
     */
    void workingDir(String dir) {
        instructions.add(new WorkDirInstruction(dir))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#workdir">WORKDIR instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     workingDir(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             '/path/to/workdir'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param dir Directory
     * @see #workingDir(String)
     * @since 4.0
     */
    void workingDir(Provider<String> provider) {
        instructions.add(new WorkDirInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#onbuild">ONBUILD instruction</a> adds to the image a
     * trigger instruction to be executed at a later time, when the image is used as the base for another build.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     onBuild('ADD . /app/src')
     * }
     * </pre>
     *
     * @param instruction Instruction
     * @see #onBuild(Provider)
     */
    void onBuild(String instruction) {
        instructions.add(new OnBuildInstruction(instruction))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#onbuild">ONBUILD instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     onBuild(project.provider(new Callable<String>() {
     *         @Override
     *         String call() throws Exception {
     *             'ADD . /app/src'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param instruction Instruction
     * @see #onBuild(String)
     * @since 4.0
     */
    void onBuild(Provider<String> provider) {
        instructions.add(new OnBuildInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#label">LABEL instruction</a> adds metadata to an image.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     label(['version': '1.0'])
     * }
     * </pre>
     *
     * @param labels Labels
     * @see #label(Provider)
     */
    void label(Map<String, String> labels) {
        instructions.add(new LabelInstruction(labels))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#label">LABEL instruction</a> as Provider.
     *
     * Example in Groovy DSL:
     *
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     label(project.provider(new Callable<Map<String, String>>() {
     *         @Override
     *         Map<String, String> call() throws Exception {
     *             ['version': '1.0']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Labels as Provider
     * @see #label(Map)
     * @since 4.0
     */
    void label(Provider<Map<String, String>> provider) {
        instructions.add(new LabelInstruction(provider))
    }

    static interface Instruction {
        @Internal
        String getKeyword()

        /**
         * @since 3.6.0
         */
        @Input
        @Optional
        String getText()
    }

    static class GenericInstruction implements Instruction {
        private final String instruction
        private final Provider<String> instructionProvider

        GenericInstruction(String instruction) {
            this.instruction = instruction
        }

        GenericInstruction(Provider<String> instructionProvider) {
            this.instructionProvider = instructionProvider
        }

        @Override
        String getKeyword() {
            if (instructionProvider) {
                parseKeyword(instructionProvider.get())
            } else {
                parseKeyword(instruction)
            }
        }

        private String parseKeyword(String inst) {
            inst.substring(0, inst.indexOf(' '))
        }

        @Override
        String getText() {
            if (instructionProvider) {
                return instructionProvider.get()
            }

            instruction
        }
    }

    static abstract class StringCommandInstruction implements Instruction {
        private final String command
        private final Provider<String> commandProvider

        StringCommandInstruction(String command) {
            this.command = command
        }

        StringCommandInstruction(Provider<String> commandProvider) {
            this.commandProvider = commandProvider
        }

        @Override
        String getText() {
            if (commandProvider) {
                return "$keyword ${commandProvider.get()}"
            }

            "$keyword $command"
        }
    }

    static abstract class StringArrayInstruction implements Instruction {
        private final String[] command
        private final Provider<List<String>> commandProvider

        StringArrayInstruction(String... command) {
            this.command = command
        }

        StringArrayInstruction(Provider<List<String>> commandProvider) {
            this.commandProvider = commandProvider
        }

        @Override
        String getText() {
            if (commandProvider) {
                return keyword + ' ["' + commandProvider.get().join('", "') + '"]'
            }

            keyword + ' ["' + command.join('", "') + '"]'
        }
    }

    interface ItemJoiner {
        String join(Map<String, String> map)
    }

    static class MultiItemJoiner implements ItemJoiner {
        @Override
        String join(Map<String, String> map) {
            map.inject([]) { result, entry ->
                def key = ItemJoinerUtil.isUnquotedStringWithWhitespaces(entry.key) ? ItemJoinerUtil.toQuotedString(entry.key) : entry.key
                def value = ItemJoinerUtil.isUnquotedStringWithWhitespaces(entry.value) ? ItemJoinerUtil.toQuotedString(entry.value) : entry.value
                value = value.replaceAll("(\r)*\n", "\\\\\n")
                result << "$key=$value"
            }.join(' ')
        }
    }

    static class SingleItemJoiner implements ItemJoiner {
        @Override
        String join(Map<String, String> map) {
            map.inject([]) { result, entry ->
                def key = ItemJoinerUtil.isUnquotedStringWithWhitespaces(entry.key) ? ItemJoinerUtil.toQuotedString(entry.key) : entry.key
                // preserve multiline value in a single item key value instruction but ignore any other whitespaces or quotings
                def value = entry.value.replaceAll("(\r)*\n", "\\\\\n")
                result << "$key $value"
            }.join('')
        }
    }

    private class ItemJoinerUtil {
        private static boolean isUnquotedStringWithWhitespaces(String str) {
            return !str.matches('["].*["]') &&
                str.matches('.*(?: |(?:\r?\n)).*')
        }

        private static String toQuotedString(final String str) {
            '"'.concat(str.replaceAll('"', '\\\\"')).concat('"')
        }
    }

    static abstract class MapInstruction implements Instruction {
        private final Map<String, String> command
        private final Provider<Map<String, String>> commandProvider
        private final ItemJoiner joiner

        MapInstruction(Map<String, String> command, ItemJoiner joiner) {
            this.command = command
            this.joiner = joiner
        }

        MapInstruction(Map<String, String> command) {
            this(command, new MultiItemJoiner())
        }

        MapInstruction(Provider<Map<String, String>> commandProvider) {
            this.commandProvider = commandProvider
            joiner = new MultiItemJoiner()
        }

        @Override
        String getText() {
            Map<String, String> commandToJoin = command

            if (commandProvider) {
                def evaluatedCommand = commandProvider.get()

                if (!(evaluatedCommand instanceof Map<String, String>)) {
                    throw new IllegalArgumentException("the given evaluated closure is not a valid input for instruction ${keyword} while it doesn't provide a `Map` ([ key: value ]) but a `${evaluatedCommand?.class}` (${evaluatedCommand?.toString()})")
                }
                commandToJoin = evaluatedCommand as Map<String, String>
            }
            if (commandToJoin == null) {
                throw new IllegalArgumentException("instruction has to be set for ${keyword}")
            }
            validateKeysAreNotBlank commandToJoin
            "$keyword ${joiner.join(commandToJoin)}"
        }

        private void validateKeysAreNotBlank(Map<String, String> command) throws IllegalArgumentException {
            command.each { entry ->
                if (entry.key.trim().length() == 0) {
                    throw new IllegalArgumentException("blank keys for a key=value pair are not allowed: please check instruction ${keyword} and given pair `${entry}`")
                }
            }
        }
    }

    static abstract class FileInstruction implements Instruction {
        private final String src
        private final String dest
        private final String flags
        private final Provider<File> provider

        FileInstruction(String src, String dest, String flags = null) {
            this.src = src
            this.dest = dest
            this.flags = flags
        }

        FileInstruction(Provider<File> provider) {
            this.provider = provider
        }

        @Override
        String getText() {
            String keyword = getKeyword()
            File file

            if (provider) {
                file = provider.get()
            } else {
                file = new File(src, dest, flags)
            }

            if (file.flags) {
                keyword += " $flags"
            }
            if (file.src && file.dest) {
                "$keyword $file.src $file.dest"
            }
        }
    }

    static class FromInstruction implements Instruction {
        private final String image
        private final String stageName
        private final Provider<From> provider

        FromInstruction(String image, String stageName = null) {
            this.image = image
            this.stageName = stageName
        }

        FromInstruction(Provider<From> provider) {
            this.provider = provider
        }

        @Override
        String getKeyword() {
            "FROM"
        }

        @Override
        String getText() {
            if (provider) {
                return buildTextInstruction(provider.get())
            }

            buildTextInstruction(new From(image, stageName))
        }

        private String buildTextInstruction(From from) {
            String result = "$keyword $from.image"

            if (from.stageName) {
                result += " AS $from.stageName"
            }

            result
        }
    }

    static class ArgInstruction extends StringCommandInstruction {
        ArgInstruction(String arg) {
            super(arg)
        }

        ArgInstruction(Provider<String> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "ARG"
        }
    }

    static class RunCommandInstruction extends StringCommandInstruction {
        RunCommandInstruction(String command) {
            super(command)
        }

        RunCommandInstruction(Provider<String> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "RUN"
        }
    }

    static class DefaultCommandInstruction extends StringArrayInstruction {
        DefaultCommandInstruction(String... command) {
            super(command)
        }

        DefaultCommandInstruction(Provider<List<String>> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "CMD"
        }
    }

    static class ExposePortInstruction implements Instruction {
        private final Integer[] ports
        private final Provider<List<Integer>> provider

        ExposePortInstruction(Integer... ports) {
            this.ports = ports
        }

        ExposePortInstruction(Provider<List<Integer>> provider) {
            this.provider = provider
        }

        @Override
        String getKeyword() {
            "EXPOSE"
        }

        @Override
        String getText() {
            if (provider) {
                List<Integer> evaluatedPorts = provider.get()

                if (!evaluatedPorts.empty) {
                    "$keyword ${evaluatedPorts.join(' ')}"
                }
            } else {
                "$keyword ${ports.join(' ')}"
            }
        }
    }

    static class EnvironmentVariableInstruction extends MapInstruction {
        EnvironmentVariableInstruction(String key, String value) {
            super([(key): value], new SingleItemJoiner())
        }

        EnvironmentVariableInstruction(Map envVars) {
            super(envVars)
        }

        EnvironmentVariableInstruction(Provider<Map<String, String>> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "ENV"
        }
    }

    static class AddFileInstruction extends FileInstruction {
        AddFileInstruction(String src, String dest) {
            super(src, dest)
        }

        AddFileInstruction(Provider<File> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "ADD"
        }
    }

    static class CopyFileInstruction extends FileInstruction {
        CopyFileInstruction(String src, String dest, String stageName = null) {
            super(src, dest, stageName ? "--from=$stageName" : null)
        }

        CopyFileInstruction(Provider<File> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "COPY"
        }
    }

    static class EntryPointInstruction extends StringArrayInstruction {
        EntryPointInstruction(String... entryPoint) {
            super(entryPoint)
        }

        EntryPointInstruction(Provider<List<String>> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "ENTRYPOINT"
        }
    }

    static class VolumeInstruction extends StringArrayInstruction {
        VolumeInstruction(String... volume) {
            super(volume)
        }

        VolumeInstruction(Provider<List<String>> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "VOLUME"
        }
    }

    static class UserInstruction extends StringCommandInstruction {
        UserInstruction(String user) {
            super(user)
        }

        UserInstruction(Provider<String> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "USER"
        }
    }

    static class WorkDirInstruction extends StringCommandInstruction {
        WorkDirInstruction(String dir) {
            super(dir)
        }

        WorkDirInstruction(Provider<String> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "WORKDIR"
        }
    }

    static class OnBuildInstruction extends StringCommandInstruction {
        OnBuildInstruction(String instruction) {
            super(instruction)
        }

        OnBuildInstruction(Provider<String> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "ONBUILD"
        }
    }

    static class LabelInstruction extends MapInstruction {
        LabelInstruction(Map labels) {
            super(labels)
        }

        LabelInstruction(Provider<Map<String, String>> provider) {
            super(provider)
        }

        @Override
        String getKeyword() {
            "LABEL"
        }
    }
}
