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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.DefaultTask
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.annotation.Nullable

/**
 * Creates a Dockerfile based on the provided instructions.
 */
@CacheableTask
@CompileStatic
class Dockerfile extends DefaultTask {

    private final ListProperty<Instruction> instructions

    /**
     * The destination file representing the Dockerfile. The destination file encourages the conventional file name Dockerfile but allows any arbitrary file name.
     * <p>
     * Defaults to {@code $buildDir/docker/Dockerfile}.
     * <p>
     * The method {@link #getDestDir()} returns the parent directory of the Dockerfile.
     */
    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty destFile

    Dockerfile() {
        instructions = project.objects.listProperty(Instruction).empty()
        destFile = project.objects.fileProperty()
        destFile.set(project.layout.buildDirectory.file('docker/Dockerfile'))
    }

    /**
     * Returns all instructions used to generate the Dockerfile.
     *
     * @return All instructions
     */
    @Nested
    ListProperty<Instruction> getInstructions() {
        instructions
    }

    /**
     * Returns a provider representing the destination directory containing the Dockerfile.
     *
     * @return The destination directory containing the Dockerfile
     * @since 4.4.0
     */
    @Internal
    Provider<Directory> getDestDir() {
        destFile.flatMap(new Transformer<Provider<Directory>, RegularFile>() {
            @Override
            Provider<Directory> transform(RegularFile f) {
                DirectoryProperty destDir = project.objects.directoryProperty()
                destDir.set(f.asFile.parentFile)
                destDir
            }
        })
    }

    @TaskAction
    void create() {
        verifyValidInstructions()

        destFile.get().asFile.withWriter { out ->
            instructions.get().forEach() { Instruction instruction ->
                String instructionText = instruction.getText()

                if (instructionText) {
                    out.println instructionText
                }
            }
        }
    }

    private void verifyValidInstructions() {
        List<Instruction> allInstructions = instructions.get().collect()

        // Comments are not relevant for validating instruction order
        allInstructions.removeAll { it.text?.startsWith(CommentInstruction.KEYWORD) }

        if (allInstructions.empty) {
            throw new IllegalStateException('Please specify instructions for your Dockerfile')
        }

        def fromPos = allInstructions.findIndexOf { it.keyword == FromInstruction.KEYWORD }
        def othersPos = allInstructions.findIndexOf { it.keyword != ArgInstruction.KEYWORD && it.keyword != FromInstruction.KEYWORD }

        if (fromPos < 0 || (othersPos >= 0 && fromPos > othersPos)) {
            throw new IllegalStateException("The first instruction of a Dockerfile has to be $FromInstruction.KEYWORD (or $ArgInstruction.KEYWORD for Docker later than 17.05)")
        }
    }

    /**
     * Adds instructions to the Dockerfile from a template file. The template file can have any name.
     *
     * @param template The template file
     * @see #instructionsFromTemplate(String)
     * @see #instructionsFromTemplate(Provider)
     */
    void instructionsFromTemplate(java.io.File template) {
        if (!template.exists()) {
            throw new FileNotFoundException("docker template file not found at location : ${template.getAbsolutePath()}")
        }
        template.readLines().findAll { it.length() > 0 } each { String instruction ->
            instructions.add(new GenericInstruction(instruction))
        }
    }

    /**
     * Adds instructions to the Dockerfile from a template file. The path can be relative to the project root directory or absolute.
     *
     * @param templatePath The path to the template file
     * @see #instructionsFromTemplate(java.io.File)
     * @see #instructionsFromTemplate(Provider)
     */
    void instructionsFromTemplate(String templatePath) {
        instructionsFromTemplate(project.file(templatePath))
    }

    /**
     * Adds instructions to the Dockerfile from a template file. Currently, the provider is evaluated as soon as the method is called
     * which means that the provider is not evaluated lazily. This behavior might change in the future.
     *
     * @param provider The provider of the template file
     * @see #instructionsFromTemplate(java.io.File)
     * @see #instructionsFromTemplate(String)
     * @since 4.0.0
     */
    void instructionsFromTemplate(Provider<RegularFile> provider) {
        instructionsFromTemplate(provider.get().asFile)
    }

    /**
     * Adds a full instruction as String.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     instruction(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'FROM ubuntu:14.04'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Instruction as Provider
     * @see #instruction(String)
     * @since 4.0.0
     */
    void instruction(Provider<String> provider) {
        instructions.add(new GenericInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#from">FROM instruction</a> sets the Base Image for
     * subsequent instructions.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     from(project.provider(new Callable<Dockerfile.From>() {
     *         {@literal @}Override
     *         Dockerfile.From call() throws Exception {
     *             new Dockerfile.From('ubuntu:14.04')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider From information as Provider
     * @see #from(String, String)
     * @since 4.0.0
     */
    void from(Provider<Dockerfile.From> provider) {
        instructions.add(new FromInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#arg">ARG instruction</a> defines a variable that
     * users can pass at build-time to the builder.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     arg(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'user1=someuser'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Argument to pass as Provider
     * @see #arg(String)
     * @since 4.0.0
     */
    void arg(Provider<String> provider) {
        instructions.add(new ArgInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#run">RUN instruction</a> will execute any commands in a
     * new layer on top of the current image and commit the results.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     runCommand(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             '/bin/bash -c echo hello'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Command as Provider
     * @see #runCommand(String)
     * @since 4.0.0
     */
    void runCommand(Provider<String> provider) {
        instructions.add(new RunCommandInstruction(provider))
    }

    /**
     * The main purpose of a <a href="https://docs.docker.com/engine/reference/builder/#cmd">CMD instruction</a> is to provide
     * defaults for an executing container.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     defaultCommand(project.provider(new Callable<List<String>>() {
     *         {@literal @}Override
     *         List<String> call() throws Exception {
     *             ['/usr/bin/wc', '--help']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Command as Provider
     * @see #defaultCommand(String...)
     * @since 4.0.0
     */
    void defaultCommand(Provider<List<String>> provider) {
        instructions.add(new DefaultCommandInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#expose">EXPOSE instruction</a> informs Docker that the
     * container will listen on the specified network ports at runtime.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     exposePort(project.provider(new Callable<List<Integer>>() {
     *         {@literal @}Override
     *         List<Integer> call() throws Exception {
     *             [8080, 9090]
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param ports Ports as Provider
     * @see #exposePort(Integer...)
     * @since 4.0.0
     */
    void exposePort(Provider<List<Integer>> provider) {
        instructions.add(new ExposePortInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#env">ENV instruction</a> sets the environment variable
     * &lt;key&gt; to the value &lt;value&gt;. This value will be passed to all future RUN instructions.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     environmentVariable(project.provider(new Callable<Map<String, String>>() {
     *         {@literal @}Override
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
     * @since 4.0.0
     */
    void environmentVariable(Provider<Map<String, String>> provider) {
        instructions.add(new EnvironmentVariableInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#add">ADD instruction</a> copies new files, directories
     * or remote file URLs from &lt;src&gt; and adds them to the filesystem of the container at the path &lt;dest&gt;.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     addFile(project.provider(new Callable<Dockerfile.File>() {
     *         {@literal @}Override
     *         Dockerfile.File call() throws Exception {
     *             new Dockerfile.File('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Add instruction as Provider
     * @see #addFile(String, String)
     * @since 4.0.0
     */
    void addFile(Provider<Dockerfile.File> provider) {
        instructions.add(new AddFileInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#copy">COPY instruction</a> copies new files or directories
     * from &lt;src&gt; and adds them to the filesystem of the container at the path &lt;dest&gt;.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     copyFile(project.provider(new Callable<Dockerfile.File>() {
     *         {@literal @}Override
     *         Dockerfile.File call() throws Exception {
     *             new Dockerfile.File('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Copy instruction as Provider
     * @see #copyFile(String, String, String)
     * @since 4.0.0
     */
    void copyFile(Provider<Dockerfile.File> provider) {
        instructions.add(new CopyFileInstruction(provider))
    }

    /**
     * An <a href="https://docs.docker.com/engine/reference/builder/#entrypoint">ENTRYPOINT</a> allows you to configure a container
     * that will run as an executable.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     entryPoint(project.provider(new Callable<List<String>>() {
     *         {@literal @}Override
     *         List<String> call() throws Exception {
     *             ['top', '-b']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param entryPoint Entry point
     * @see #entryPoint(String...)
     * @since 4.0.0
     */
    void entryPoint(Provider<List<String>> provider) {
        instructions.add(new EntryPointInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#volume">VOLUME instruction</a> will create a mount point
     * with the specified name and mark it as holding externally mounted volumes from native host or other containers.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     volume(project.provider(new Callable<List<String>>() {
     *         {@literal @}Override
     *         List<String> call() throws Exception {
     *             ['/myvol']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param volume Volume
     * @see #volume(String...)
     * @since 4.0.0
     */
    void volume(Provider<List<String>> provider) {
        instructions.add(new VolumeInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#user">USER instruction</a> sets the user name or UID to
     * use when running the image and for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     user(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'patrick'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider User as Provider
     * @see #user(String)
     * @since 4.0.0
     */
    void user(Provider<String> provider) {
        instructions.add(new UserInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#workdir">WORKDIR instruction</a> sets the working directory
     * for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     workingDir(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             '/path/to/workdir'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param dir Directory
     * @see #workingDir(String)
     * @since 4.0.0
     */
    void workingDir(Provider<String> provider) {
        instructions.add(new WorkDirInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#onbuild">ONBUILD instruction</a> adds to the image a
     * trigger instruction to be executed at a later time, when the image is used as the base for another build.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     onBuild(project.provider(new Callable<String>() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'ADD . /app/src'
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param instruction Instruction
     * @see #onBuild(String)
     * @since 4.0.0
     */
    void onBuild(Provider<String> provider) {
        instructions.add(new OnBuildInstruction(provider))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#label">LABEL instruction</a> adds metadata to an image.
     * <p>
     * Example in Groovy DSL:
     * <p>
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
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     label(project.provider(new Callable<Map<String, String>>() {
     *         {@literal @}Override
     *         Map<String, String> call() throws Exception {
     *             ['version': '1.0']
     *         }
     *     }))
     * }
     * </pre>
     *
     * @param provider Labels as Provider
     * @see #label(Map)
     * @since 4.0.0
     */
    void label(Provider<Map<String, String>> provider) {
        instructions.add(new LabelInstruction(provider))
    }

    /**
     * A representation of an instruction in a Dockerfile.
     */
    static interface Instruction {
        /**
         * Gets the keyword of the instruction as used in the Dockerfile.
         * <p>
         * For example the keyword of the {@link FromInstruction} is {@code FROM}.
         *
         * @return The instruction keyword
         */
        @Internal
        @Nullable
        String getKeyword()

        /**
         * Gets the full text of the instruction as used in the Dockerfile.
         *
         * @return The instruction
         * @since 3.6.0
         */
        @Input
        @Optional
        @Nullable
        String getText()
    }

    /**
     * An instruction that uses the provided value as-is without any additional formatting.
     * <p>
     * Use this instruction if you want to provide a very complex instruction or if there's not a specific implementation of {@link Instruction} that serves your use case.
     */
    static class GenericInstruction implements Instruction {
        private final String instruction
        private final Provider<String> instructionProvider

        GenericInstruction(String instruction) {
            this.instruction = instruction
        }

        GenericInstruction(Provider<String> instructionProvider) {
            this.instructionProvider = instructionProvider
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            if (instructionProvider) {
                parseKeyword(instructionProvider.getOrNull())
            } else {
                parseKeyword(instruction)
            }
        }

        private String parseKeyword(String inst) {
            inst?.substring(0, inst.indexOf(' '))
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            if (instructionProvider) {
                return instructionProvider.getOrNull()
            }

            instruction
        }
    }

    /**
     * An instruction whose value is a String.
     */
    static abstract class StringCommandInstruction implements Instruction {
        private final String command
        private final Provider<String> commandProvider

        StringCommandInstruction(String command) {
            this.command = command
        }

        StringCommandInstruction(Provider<String> commandProvider) {
            this.commandProvider = commandProvider
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            if (commandProvider) {
                String command = commandProvider.getOrNull()

                if (command) {
                    return buildText(command)
                }
            } else {
                return buildText(command)
            }
        }

        private String buildText(String command) {
            "$keyword $command"
        }
    }

    /**
     * An instruction whose value is a String array.
     */
    static abstract class StringArrayInstruction implements Instruction {
        private final String[] command
        private final Provider<List<String>> commandProvider

        StringArrayInstruction(String... command) {
            this.command = command
        }

        StringArrayInstruction(Provider<List<String>> commandProvider) {
            this.commandProvider = commandProvider
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            if (commandProvider) {
                List<String> command = commandProvider.getOrNull()

                if (command) {
                    return buildText(command as String[])
                }
            } else {
                return buildText(command)
            }
        }

        private String buildText(String[] command) {
            keyword + ' ["' + command.join('", "') + '"]'
        }
    }

    interface ItemJoiner {
        String join(Map<String, String> map)
    }

    static class MultiItemJoiner implements ItemJoiner {
        @Override
        @CompileStatic(TypeCheckingMode.SKIP)
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
        @CompileStatic(TypeCheckingMode.SKIP)
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

    /**
     * An instruction whose value is a Map.
     */
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

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            Map<String, String> commandToJoin = command

            if (commandProvider) {
                def evaluatedCommand = commandProvider.getOrNull()

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

    /**
     * An instruction whose value is a File.
     */
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

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            String keyword = getKeyword()
            File file

            if (provider) {
                file = provider.getOrNull()
            } else {
                file = new File(src, dest, flags)
            }

            if (file) {
                if (file.flags) {
                    keyword += " $file.flags"
                }
                if (file.src && file.dest) {
                    "$keyword $file.src $file.dest"
                }
            }
        }
    }

    /**
     * Represents a {@code FROM} instruction.
     */
    static class FromInstruction implements Instruction {
        public static final String KEYWORD = 'FROM'
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

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            KEYWORD
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            if (provider) {
                return buildTextInstruction(provider.getOrNull())
            }

            buildTextInstruction(new From(image, stageName))
        }

        private String buildTextInstruction(From from) {
            if (from) {
                String result = "$keyword $from.image"

                if (from.stageName) {
                    result += " AS $from.stageName"
                }

                result
            }
        }
    }

    /**
     * Represents a {@code ARG} instruction.
     */
    static class ArgInstruction extends StringCommandInstruction {
        public static final String KEYWORD = 'ARG'

        ArgInstruction(String arg) {
            super(arg)
        }

        ArgInstruction(Provider<String> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            KEYWORD
        }
    }

    /**
     * Represents a {@code RUN} instruction.
     */
    static class RunCommandInstruction extends StringCommandInstruction {
        RunCommandInstruction(String command) {
            super(command)
        }

        RunCommandInstruction(Provider<String> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "RUN"
        }
    }

    /**
     * Represents a {@code CMD} instruction.
     */
    static class DefaultCommandInstruction extends StringArrayInstruction {
        DefaultCommandInstruction(String... command) {
            super(command)
        }

        DefaultCommandInstruction(Provider<List<String>> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "CMD"
        }
    }

    /**
     * Represents a {@code EXPOSE} instruction.
     */
    static class ExposePortInstruction implements Instruction {
        private final Integer[] ports
        private final Provider<List<Integer>> provider

        ExposePortInstruction(Integer... ports) {
            this.ports = ports
        }

        ExposePortInstruction(Provider<List<Integer>> provider) {
            this.provider = provider
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "EXPOSE"
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            if (provider) {
                List<Integer> evaluatedPorts = provider.getOrNull()

                if (evaluatedPorts && !evaluatedPorts.isEmpty()) {
                    return buildText(evaluatedPorts as Integer[])
                }
            } else {
                return buildText(ports)
            }
        }

        private String buildText(Integer[] ports) {
            "$keyword ${ports.join(' ')}"
        }
    }

    /**
     * Represents a {@code ENV} instruction.
     */
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

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "ENV"
        }
    }

    /**
     * Represents a {@code ADD} instruction.
     */
    static class AddFileInstruction extends FileInstruction {
        AddFileInstruction(String src, String dest) {
            super(src, dest)
        }

        AddFileInstruction(Provider<File> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "ADD"
        }
    }

    /**
     * Represents a {@code COPY} instruction.
     */
    static class CopyFileInstruction extends FileInstruction {
        CopyFileInstruction(String src, String dest, String stageName = null) {
            super(src, dest, stageName ? "--from=$stageName" : null)
        }

        CopyFileInstruction(Provider<File> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "COPY"
        }
    }

    /**
     * Represents a {@code ENTRYPOINT} instruction.
     */
    static class EntryPointInstruction extends StringArrayInstruction {
        EntryPointInstruction(String... entryPoint) {
            super(entryPoint)
        }

        EntryPointInstruction(Provider<List<String>> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
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

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "VOLUME"
        }
    }

    /**
     * Represents a {@code USER} instruction.
     */
    static class UserInstruction extends StringCommandInstruction {
        UserInstruction(String user) {
            super(user)
        }

        UserInstruction(Provider<String> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "USER"
        }
    }

    /**
     * Represents a {@code WORKDIR} instruction.
     */
    static class WorkDirInstruction extends StringCommandInstruction {
        WorkDirInstruction(String dir) {
            super(dir)
        }

        WorkDirInstruction(Provider<String> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "WORKDIR"
        }
    }

    /**
     * Represents a {@code ONBUILD} instruction.
     */
    static class OnBuildInstruction extends StringCommandInstruction {
        OnBuildInstruction(String instruction) {
            super(instruction)
        }

        OnBuildInstruction(Provider<String> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "ONBUILD"
        }
    }

    /**
     * Represents a {@code LABEL} instruction.
     */
    static class LabelInstruction extends MapInstruction {
        LabelInstruction(Map labels) {
            super(labels)
        }

        LabelInstruction(Provider<Map<String, String>> provider) {
            super(provider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            "LABEL"
        }
    }

    /**
     * Represents a comment instruction.
     *
     * @since 4.0.1
     */
    static class CommentInstruction extends StringCommandInstruction {
        public static final String KEYWORD = '#'

        CommentInstruction(String command) {
            super(command)
        }

        CommentInstruction(Provider<String> commandProvider) {
            super(commandProvider)
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getKeyword() {
            KEYWORD
        }
    }

    /**
     * Input data for a {@link AddFileInstruction} or {@link CopyFileInstruction}.
     *
     * @since 4.0.0
     */
    static class File {
        final String src
        final String dest
        final @Nullable String flags

        File(String src, String dest) {
            this.src = src
            this.dest = dest
        }

        File(String src, String dest, @Nullable String flags) {
            this.src = src
            this.dest = dest
            this.flags = flags
        }
    }

    /**
     * Input data for a {@link FromInstruction}.
     *
     * @since 4.0.0
     */
    static class From {
        final String image
        final @Nullable String stageName

        From(String image) {
            this.image = image
        }

        From(String image, @Nullable String stageName) {
            this.image = image
            this.stageName = stageName
        }
    }
}
