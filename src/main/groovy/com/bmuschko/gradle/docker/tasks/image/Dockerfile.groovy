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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
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
    final RegularFileProperty destFile

    private final ObjectFactory objects

    Dockerfile() {
        instructions = project.objects.listProperty(Instruction).empty()
        destFile = project.objects.fileProperty()
        destFile.set(project.layout.buildDirectory.file('docker/Dockerfile'))
        objects = project.objects
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
                DirectoryProperty destDir = objects.directoryProperty()
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
     * The produced instructions look as follows:
     * <p>
     * <pre>
     * FROM ubuntu:14.04
     * LABEL maintainer=benjamin.muschko{@literal @}gmail.com
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
     *     instruction(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'FROM ubuntu:14.04'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * FROM ubuntu:14.04
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * FROM ubuntu:14.04
     * </pre>
     *
     * @param from From definition
     * @see #from(From)
     * @see #from(Provider)
     */
    void from(String image) {
        instructions.add(new FromInstruction(new From(image)))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#from">FROM instruction</a> sets the Base Image for
     * subsequent instructions.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     from(new From('ubuntu:14.04'))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * FROM ubuntu:14.04
     * </pre>
     *
     * @param from From definition
     * @param stageName stage name in case of multi-stage builds (default null)
     * @see #from(String)
     * @see #from(Provider)
     */
    void from(From from) {
        instructions.add(new FromInstruction(from))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#from">FROM instruction</a> as Provider.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     from(project.provider(new Callable&#60;Dockerfile.From&#62;() {
     *         {@literal @}Override
     *         Dockerfile.From call() throws Exception {
     *             new Dockerfile.From('ubuntu:14.04')
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * FROM ubuntu:14.04
     * </pre>
     *
     * @param provider From information as Provider
     * @see #from(From)
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ARG user1=someuser
     * </pre>
     *
     * @param arg Argument to pass, possibly with default value.
     * @see #arg(Provider)
     */
    void arg(String arg) {
        instructions.add(new ArgInstruction(arg))
    }

    /**
     * An <a href="https://docs.docker.com/engine/reference/builder/#arg">ARG instruction</a> as Provider.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     arg(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'user1=someuser'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ARG user1=someuser
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * RUN /bin/bash -c echo hello
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
     *     runCommand(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             '/bin/bash -c echo hello'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * RUN /bin/bash -c echo hello
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * CMD ["/usr/bin/wc", "--help"]
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
     *     defaultCommand(project.provider(new Callable&#60;List&#60;String&#62;&#62;() {
     *         {@literal @}Override
     *         List&#60;String&#62; call() throws Exception {
     *             ['/usr/bin/wc', '--help']
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * CMD ["/usr/bin/wc", "--help"]
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * EXPOSE 8080 9090
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
     *     exposePort(project.provider(new Callable&#60;List&#60;Integer&#62;&#62;() {
     *         {@literal @}Override
     *         List&#60;Integer&#62; call() throws Exception {
     *             [8080, 9090]
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * EXPOSE 8080 9090
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
     *     environmentVariable('MY_NAME', 'John Doe')
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ENV MY_NAME=John Doe
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
     *     environmentVariable(['MY_NAME': 'John Doe'])
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ENV MY_NAME=John Doe
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
     *     environmentVariable(project.provider(new Callable&#60;Map&#60;String, String&#62;&#62;() {
     *         {@literal @}Override
     *         Map&#60;String, String&#62; call() throws Exception {
     *             ['MY_NAME': 'John Doe']
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ENV MY_NAME=John Doe
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ADD test /absoluteDir/
     * </pre>
     *
     * @param src The source path
     * @param dest The destination path
     * @see #addFile(Dockerfile.File)
     * @see #addFile(Provider)
     */
    void addFile(String src, String dest) {
        addFile(new File(src, dest))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#add">ADD instruction</a> copies new files, directories
     * or remote file URLs from &lt;src&gt; and adds them to the filesystem of the container at the path &lt;dest&gt;.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     addFile(new Dockerfile.File('test', '/absoluteDir/'))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ADD test /absoluteDir/
     * </pre>
     *
     * @param file Dockerfile.File definition
     * @see #addFile(String, String)
     * @see #addFile(Provider)
     */
    void addFile(Dockerfile.File file) {
        instructions.add(new AddFileInstruction(file))
    }

    /**
     * An <a href="https://docs.docker.com/engine/reference/builder/#add">ADD instruction</a> as Provider.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     addFile(project.provider(new Callable&#60;Dockerfile.File&#62;() {
     *         {@literal @}Override
     *         Dockerfile.File call() throws Exception {
     *             new Dockerfile.File('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ADD test /absoluteDir/
     * </pre>
     *
     * @param provider Add instruction as Provider
     * @see #addFile(String, String)
     * @see #addFile(Dockerfile.File)
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * COPY test /absoluteDir/
     * </pre>
     *
     * @param src The source path
     * @param dest The destination path
     * @see #copyFile(CopyFile)
     * @see #copyFile(Provider)
     */
    void copyFile(String src, String dest) {
        copyFile(new CopyFile(src, dest))
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#copy">COPY instruction</a> copies new files or directories
     * from &lt;src&gt; and adds them to the filesystem of the container at the path &lt;dest&gt;.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     copyFile(new Dockerfile.CopyFile('test', '/absoluteDir/'))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * COPY test /absoluteDir/
     * </pre>
     *
     * @param file CopyFile definition
     * @see #copyFile(String, String)
     * @see #copyFile(Provider)
     */
    void copyFile(CopyFile file) {
        instructions.add(new CopyFileInstruction(file))
    }

    /**
     * A <a href="https://docs.docker.com/engine/reference/builder/#copy">COPY instruction</a> as Provider.
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     copyFile(project.provider(new Callable&#60;Dockerfile.CopyFile&#62;() {
     *         {@literal @}Override
     *         Dockerfile.CopyFile call() throws Exception {
     *             new Dockerfile.CopyFile('test', '/absoluteDir/')
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * COPY test /absoluteDir/
     * </pre>
     *
     * @param provider Copy instruction as Provider
     * @see #copyFile(String, String)
     * @see #copyFile(CopyFile)
     * @since 4.0.0
     */
    void copyFile(Provider<Dockerfile.CopyFile> provider) {
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ENTRYPOINT ["top", "-b"]
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
     *     entryPoint(project.provider(new Callable&#60;List&#60;String&#62;&#62;() {
     *         {@literal @}Override
     *         List&#60;String&#62; call() throws Exception {
     *             ['top', '-b']
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ENTRYPOINT ["top", "-b"]
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * VOLUME ["/myvol"]
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
     *     volume(project.provider(new Callable&#60;List&#60;String&#62;&#62;() {
     *         {@literal @}Override
     *         List&#60;String&#62; call() throws Exception {
     *             ['/myvol']
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * VOLUME ["/myvol"]
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * USER patrick
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
     *     user(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'patrick'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * USER patrick
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * WORKDIR /path/to/workdir
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
     *     workingDir(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             '/path/to/workdir'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * WORKDIR /path/to/workdir
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ONBUILD ADD . /app/src
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
     *     onBuild(project.provider(new Callable&#60;String&#62;() {
     *         {@literal @}Override
     *         String call() throws Exception {
     *             'ADD . /app/src'
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * ONBUILD ADD . /app/src
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
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * LABEL version=1.0
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
     *     label(project.provider(new Callable&#60;Map&#60;String, String&#62;&#62;() {
     *         {@literal @}Override
     *         Map&#60;String, String&#62; call() throws Exception {
     *             ['version': '1.0']
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * LABEL version=1.0
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
                return parseKeyword(instructionProvider.getOrNull())
            }

            parseKeyword(instruction)
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

    private interface ItemJoiner {
        String join(Map<String, String> map)
    }

    private static class MultiItemJoiner implements ItemJoiner {
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

    private static class ItemJoinerUtil {
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

        MapInstruction(Map<String, String> command) {
            this.command = command
            this.joiner = new MultiItemJoiner()
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
     * An instruction whose value is a Dockerfile.File.
     */
    static abstract class FileInstruction<T extends Dockerfile.File> implements Instruction {
        private final T file
        private final Provider<T> provider

        FileInstruction(T file) {
            this.file = file
        }

        FileInstruction(Provider<T> provider) {
            this.provider = provider
        }

        @Internal
        T getFile() {
            provider ? provider.getOrNull() : file
        }

        /**
         * {@inheritDoc}
         */
        @Override
        String getText() {
            File fileValue = getFile()

            if (fileValue) {
                StringBuilder instruction = new StringBuilder(keyword)

                if (fileValue.chown) {
                    instruction.append(" --chown=$fileValue.chown")
                }
                if (fileValue.src && fileValue.dest) {
                    instruction.append(" $fileValue.src $fileValue.dest")
                }

                instruction.toString()
            }
        }
    }

    /**
     * Represents a {@code FROM} instruction.
     */
    static class FromInstruction implements Instruction {
        public static final String KEYWORD = 'FROM'
        private final From from
        private final Provider<From> provider

        FromInstruction(From from) {
            this.from = from
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

            buildTextInstruction(from)
        }

        private String buildTextInstruction(From from) {
            if (from) {
                String result = "$keyword $from.image"

                if (from.stage) {
                    result += " AS $from.stage"
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
        public static final String KEYWORD = 'RUN'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code CMD} instruction.
     */
    static class DefaultCommandInstruction extends StringArrayInstruction {
        public static final String KEYWORD = 'CMD'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code EXPOSE} instruction.
     */
    static class ExposePortInstruction implements Instruction {
        public static final String KEYWORD = 'EXPOSE'
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
            KEYWORD
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
        public static final String KEYWORD = 'ENV'

        EnvironmentVariableInstruction(String key, String value) {
            super([(key): value])
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
            KEYWORD
        }
    }

    /**
     * Represents a {@code ADD} instruction.
     */
    static class AddFileInstruction extends FileInstruction<File> {
        public static final String KEYWORD = 'ADD'

        AddFileInstruction(File file) {
            super(file)
        }

        AddFileInstruction(Provider<File> provider) {
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
     * Represents a {@code COPY} instruction.
     */
    static class CopyFileInstruction extends FileInstruction<CopyFile> {
        public static final String KEYWORD = 'COPY'

        CopyFileInstruction(CopyFile file) {
            super(file)
        }

        CopyFileInstruction(Provider<CopyFile> provider) {
            super(provider)
        }

        @Override
        String getText() {
            String text = super.getText()

            if (file && file.stage) {
                int keywordIndex = keyword.length()
                text = text.substring(0, keywordIndex) + " --from=$file.stage" + text.substring(keywordIndex, text.length())
            }

            text
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
     * Represents a {@code ENTRYPOINT} instruction.
     */
    static class EntryPointInstruction extends StringArrayInstruction {
        public static final String KEYWORD = 'ENTRYPOINT'

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
            KEYWORD
        }
    }

    static class VolumeInstruction extends StringArrayInstruction {
        public static final String KEYWORD = 'VOLUME'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code USER} instruction.
     */
    static class UserInstruction extends StringCommandInstruction {
        public static final String KEYWORD = 'USER'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code WORKDIR} instruction.
     */
    static class WorkDirInstruction extends StringCommandInstruction {
        public static final String KEYWORD = 'WORKDIR'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code ONBUILD} instruction.
     */
    static class OnBuildInstruction extends StringCommandInstruction {
        public static final String KEYWORD = 'ONBUILD'

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
            KEYWORD
        }
    }

    /**
     * Represents a {@code LABEL} instruction.
     */
    static class LabelInstruction extends MapInstruction {
        public static final String KEYWORD = 'LABEL'

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
            KEYWORD
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
        private final String src
        private final String dest

        @Nullable
        private String chown

        File(String src, String dest) {
            this.src = src
            this.dest = dest
        }

        /**
         * Specifies a given username, groupname, or UID/GID combination to request specific ownership of the copied content with the help of the {@code --chown} option.
         * <p>
         * Should be provided in the form of {@code <user>:<group>}.
         *
         * @param chown The ownership of the copied content
         */
        File withChown(String chown) {
            this.chown = chown
            this
        }

        /**
         * Return the source path.
         *
         * @return The source path
         */
        String getSrc() {
            src
        }

        /**
         * Returns the destination path.
         *
         * @return The destination path
         */
        String getDest() {
            dest
        }

        /**
         * Returns the ownership of the copied content.
         *
         * @return The ownership of the copied content
         */
        @Nullable
        String getChown() {
            chown
        }
    }

    /**
     * Input data for a {@link CopyFileInstruction}.
     *
     * @since 5.0.0
     */
    static class CopyFile extends File {
        @Nullable
        private String stage

        CopyFile(String src, String dest) {
            super(src, dest)
        }

        /**
         * Used to set the source location to a previous build stage.
         *
         * @param The previous stage
         * @return This instruction
         */
        CopyFile withStage(String stage) {
            this.stage = stage
            this
        }

        /**
         * Returns the previous build stage.
         *
         * @return The previous stage
         */
        @Nullable
        String getStage() {
            stage
        }
    }

    /**
     * Input data for a {@link FromInstruction}.
     *
     * @since 4.0.0
     */
    static class From {
        private final String image

        @Nullable
        private String stage

        From(String image) {
            this.image = image
        }

        /**
         * Sets a new build stage by adding {@code AS} name to the {@code FROM} instruction.
         *
         * @param stage The stage
         * @return This instruction
         */
        From withStage(String stage) {
            this.stage = stage
            this
        }

        /**
         * Returns the base image.
         *
         * @return The base image
         */
        String getImage() {
            image
        }

        /**
         * Returns the stage.
         *
         * @return The stage
         */
        @Nullable
        String getStage() {
            stage
        }
    }
}
