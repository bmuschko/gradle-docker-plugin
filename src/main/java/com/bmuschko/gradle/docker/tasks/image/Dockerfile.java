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
package com.bmuschko.gradle.docker.tasks.image;

import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Creates a Dockerfile based on the provided instructions.
 */
@CacheableTask
public class Dockerfile extends DefaultTask {

    private final ListProperty<Instruction> instructions;

    /**
     * The destination file representing the Dockerfile. The destination file encourages the conventional file name Dockerfile but allows any arbitrary file name.
     * <p>
     * Defaults to {@code $buildDir/docker/Dockerfile}.
     * <p>
     * The method {@link #getDestDir()} returns the parent directory of the Dockerfile.
     */
    @OutputFile
    public final RegularFileProperty getDestFile() {
        return destFile;
    }

    private final RegularFileProperty destFile;

    private final ObjectFactory objects;

    public Dockerfile() {
        instructions = getProject().getObjects().listProperty(Instruction.class);
        destFile = getProject().getObjects().fileProperty();
        destFile.convention(getProject().getLayout().getBuildDirectory().file("docker/Dockerfile"));
        objects = getProject().getObjects();
    }

    /**
     * Returns all instructions used to generate the Dockerfile.
     *
     * @return All instructions
     */
    @Nested
    public ListProperty<Instruction> getInstructions() {
        return instructions;
    }

    /**
     * Returns a provider representing the destination directory containing the Dockerfile.
     *
     * @return The destination directory containing the Dockerfile
     * @since 4.4.0
     */
    @Internal
    public Provider<Directory> getDestDir() {
        return destFile.flatMap(new Transformer<Provider<Directory>, RegularFile>() {
            @Override
            public Provider<Directory> transform(RegularFile f) {
                DirectoryProperty destDir = objects.directoryProperty();
                destDir.set(f.getAsFile().getParentFile());
                return destDir;
            }
        });
    }

    @TaskAction
    public void create() throws IOException {
        verifyValidInstructions();

        try (PrintWriter out = new PrintWriter(destFile.get().getAsFile())) {
            instructions.get().forEach(instruction -> {
                String instructionText = instruction.getText();

                if (instructionText != null && !instructionText.isEmpty()) {
                    out.println(instructionText);
                }
            });
        }
    }

    private void verifyValidInstructions() {
        List<Instruction> allInstructions = new ArrayList<>(instructions.get());

        // Comments are not relevant for validating instruction order
        allInstructions.removeIf(it -> {
            String text = it.getText();
            if (text == null) {
                return false;
            }
            return text.startsWith(CommentInstruction.KEYWORD);
        });

        if (allInstructions.isEmpty()) {
            throw new IllegalStateException("Please specify instructions for your Dockerfile");
        }

        int fromPos = IntStream.range(0, allInstructions.size())
                .filter(index -> {
                    Instruction it = allInstructions.get(index);
                    return it.getKeyword().equals(FromInstruction.KEYWORD);
                })
                .findFirst()
                .orElse(-1);
        int othersPos = IntStream.range(0, allInstructions.size())
                .filter(index -> {
                    Instruction it = allInstructions.get(index);
                    return !it.getKeyword().equals(ArgInstruction.KEYWORD) && !it.getKeyword().equals(FromInstruction.KEYWORD);
                })
                .findFirst()
                .orElse(-1);
        if (fromPos < 0 || (othersPos >= 0 && fromPos > othersPos)) {
            throw new IllegalStateException("The first instruction of a Dockerfile has to be " + FromInstruction.KEYWORD + " (or " + ArgInstruction.KEYWORD + " for Docker later than 17.05)");
        }
    }

    /**
     * Adds instructions to the Dockerfile from a template file. The template file can have any name.
     *
     * @param template The template file
     * @see #instructionsFromTemplate(String)
     * @see #instructionsFromTemplate(Provider)
     */
    public void instructionsFromTemplate(final java.io.File template) throws IOException {
        if (!template.exists()) {
            throw new FileNotFoundException("docker template file not found at location : " + template.getAbsolutePath());
        }

        try (Stream<String> lines = Files.lines(template.toPath())) {
            lines
                    .filter(it -> !it.isEmpty())
                    .forEach(instruction -> {
                        getInstructions().add(new GenericInstruction(instruction));
                    });
        }
    }

    /**
     * Adds instructions to the Dockerfile from a template file. The path can be relative to the project root directory or absolute.
     *
     * @param templatePath The path to the template file
     * @see #instructionsFromTemplate(java.io.File)
     * @see #instructionsFromTemplate(Provider)
     */
    public void instructionsFromTemplate(String templatePath) throws IOException {
        instructionsFromTemplate(getProject().file(templatePath));
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
    public void instructionsFromTemplate(Provider<RegularFile> provider) throws IOException {
        instructionsFromTemplate(provider.get().getAsFile());
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
    public void instruction(String instruction) {
        instructions.add(new GenericInstruction(instruction));
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
    public void instruction(Provider<String> provider) {
        instructions.add(new GenericInstruction(provider));
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
     * @param image From definition
     * @see #from(From)
     * @see #from(Provider)
     */
    public void from(String image) {
        instructions.add(new FromInstruction(new From(image)));
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
     * @see #from(String)
     * @see #from(Provider)
     */
    public void from(From from) {
        instructions.add(new FromInstruction(from));
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
    public void from(Provider<From> provider) {
        instructions.add(new FromInstruction(provider));
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
    public void arg(String arg) {
        instructions.add(new ArgInstruction(arg));
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
    public void arg(Provider<String> provider) {
        instructions.add(new ArgInstruction(provider));
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
    public void runCommand(String command) {
        instructions.add(new RunCommandInstruction(command));
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
    public void runCommand(Provider<String> provider) {
        instructions.add(new RunCommandInstruction(provider));
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
    public void defaultCommand(String... command) {
        instructions.add(new DefaultCommandInstruction(command));
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
    public void defaultCommand(Provider<List<String>> provider) {
        instructions.add(new DefaultCommandInstruction(provider));
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
    public void exposePort(Integer... ports) {
        instructions.add(new ExposePortInstruction(ports));
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
     * @param provider Ports as Provider
     * @see #exposePort(Integer...)
     * @since 4.0.0
     */
    public void exposePort(Provider<List<Integer>> provider) {
        instructions.add(new ExposePortInstruction(provider));
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
     * @param key   Key
     * @param value Value
     * @see #environmentVariable(Map)
     * @see #environmentVariable(Provider)
     */
    public void environmentVariable(String key, String value) {
        instructions.add(new EnvironmentVariableInstruction(key, value));
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
    public void environmentVariable(Map<String, String> envVars) {
        instructions.add(new EnvironmentVariableInstruction(envVars));
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
    public void environmentVariable(Provider<Map<String, String>> provider) {
        instructions.add(new EnvironmentVariableInstruction(provider));
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
     * @param src  The source path
     * @param dest The destination path
     * @see #addFile(File)
     * @see #addFile(Provider)
     */
    public void addFile(String src, String dest) {
        addFile(new File(src, dest));
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
    public void addFile(File file) {
        instructions.add(new AddFileInstruction(file));
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
     * @see #addFile(File)
     * @since 4.0.0
     */
    public void addFile(Provider<File> provider) {
        instructions.add(new AddFileInstruction(provider));
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
     * @param src  The source path
     * @param dest The destination path
     * @see #copyFile(CopyFile)
     * @see #copyFile(Provider)
     */
    public void copyFile(String src, String dest) {
        copyFile(new CopyFile(src, dest));
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
    public void copyFile(CopyFile file) {
        instructions.add(new CopyFileInstruction(file));
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
    public void copyFile(Provider<CopyFile> provider) {
        instructions.add(new CopyFileInstruction(provider));
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
    public void entryPoint(String... entryPoint) {
        instructions.add(new EntryPointInstruction(entryPoint));
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
     * @param provider Entry point
     * @see #entryPoint(String...)
     * @since 4.0.0
     */
    public void entryPoint(Provider<List<String>> provider) {
        instructions.add(new EntryPointInstruction(provider));
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
    public void volume(String... volume) {
        instructions.add(new VolumeInstruction(volume));
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
     * @param provider Volume
     * @see #volume(String...)
     * @since 4.0.0
     */
    public void volume(Provider<List<String>> provider) {
        instructions.add(new VolumeInstruction(provider));
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
    public void user(String user) {
        instructions.add(new UserInstruction(user));
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
    public void user(Provider<String> provider) {
        instructions.add(new UserInstruction(provider));
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
    public void workingDir(String dir) {
        instructions.add(new WorkDirInstruction(dir));
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
     * @param provider Directory
     * @see #workingDir(String)
     * @since 4.0.0
     */
    public void workingDir(Provider<String> provider) {
        instructions.add(new WorkDirInstruction(provider));
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
    public void onBuild(String instruction) {
        instructions.add(new OnBuildInstruction(instruction));
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
     * @param provider Instruction
     * @see #onBuild(String)
     * @since 4.0.0
     */
    public void onBuild(Provider<String> provider) {
        instructions.add(new OnBuildInstruction(provider));
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
    public void label(Map<String, String> labels) {
        instructions.add(new LabelInstruction(labels));
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
    public void label(Provider<Map<String, String>> provider) {
        instructions.add(new LabelInstruction(provider));
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#healthcheck">HEALTHCHECK instruction</a> tells
     * Docker how to test a container to check that it is still working.
     *
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     healthcheck(new Healthcheck("curl -f http://localhost/ || exit 1").withRetries(5))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * HEALTHCHECK --retries=5 CMD curl -f http://localhost/ || exit 1
     * </pre>
     *
     * @param healthcheck the healthcheck configuration
     * @see #healthcheck(Provider)
     * @see Healthcheck
     */
    public void healthcheck(Healthcheck healthcheck) {
        instructions.add(new HealthcheckInstruction(healthcheck));
    }

    /**
     * The <a href="https://docs.docker.com/engine/reference/builder/#healthcheck">HEALTHCHECK instruction</a> tells
     * Docker how to test a container to check that it is still working.
     *
     * <p>
     * Example in Groovy DSL:
     * <p>
     * <pre>
     * task createDockerfile(type: Dockerfile) {
     *     from(project.provider(new Callable&#60;Dockerfile.Healthcheck&#62;() {
     *         {@literal @}Override
     *         Dockerfile.Healthcheck call() throws Exception {
     *             new Dockerfile.Healthcheck("curl -f http://localhost/ || exit 1")
     *         }
     *     }))
     * }
     * </pre>
     * The produced instruction looks as follows:
     * <p>
     * <pre>
     * HEALTHCHECK CMD curl -f http://localhost/ || exit 1
     * </pre>
     *
     * @param provider Healthcheck information as Provider
     * @see #healthcheck(Healthcheck)
     */
    public void healthcheck(Provider<Healthcheck> provider) {
        instructions.add(new HealthcheckInstruction(provider));
    }

    /**
     * A representation of an instruction in a Dockerfile.
     */
    public interface Instruction {
        /**
         * Gets the keyword of the instruction as used in the Dockerfile.
         * <p>
         * For example the keyword of the {@link FromInstruction} is {@code FROM}.
         *
         * @return The instruction keyword
         */
        @Internal
        @Nullable
        String getKeyword();

        /**
         * Gets the full text of the instruction as used in the Dockerfile.
         *
         * @return The instruction
         * @since 3.6.0
         */
        @Input
        @Optional
        @Nullable
        String getText();
    }

    /**
     * An instruction that uses the provided value as-is without any additional formatting.
     * <p>
     * Use this instruction if you want to provide a very complex instruction or if there's not a specific implementation of {@link Instruction} that serves your use case.
     */
    public static class GenericInstruction implements Instruction {
        private final Provider<String> instructionProvider;

        public GenericInstruction(String instruction) {
            this.instructionProvider = Providers.ofNullable(instruction);
        }

        public GenericInstruction(Provider<String> instructionProvider) {
            this.instructionProvider = instructionProvider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return parseKeyword(instructionProvider.getOrNull());
        }

        private String parseKeyword(String inst) {
            return inst.substring(0, inst.indexOf(" "));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            return instructionProvider.getOrNull();
        }
    }

    /**
     * An instruction whose value is a String.
     */
    public static abstract class StringCommandInstruction implements Instruction {
        private final Provider<String> commandProvider;

        public StringCommandInstruction(String command) {
            this.commandProvider = Providers.ofNullable(command);
        }

        public StringCommandInstruction(Provider<String> commandProvider) {
            this.commandProvider = commandProvider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            String command = commandProvider.getOrNull();

            if (command != null) {
                return buildText(command);
            }


            return null;
        }

        private String buildText(String command) {
            return getKeyword() + " " + command;
        }
    }

    /**
     * An instruction whose value is a String array.
     */
    public static abstract class StringArrayInstruction implements Instruction {
        private final Provider<List<String>> commandProvider;

        public StringArrayInstruction(String... command) {
            this.commandProvider = Providers.ofNullable(new ArrayList<>(List.of(command)));
        }

        public StringArrayInstruction(Provider<List<String>> commandProvider) {
            this.commandProvider = commandProvider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            List<String> command = commandProvider.getOrNull();

            if (command != null && !command.isEmpty()) {
                return buildText(command);
            }


            return null;
        }

        private String buildText(List<String> command) {
            return getKeyword() + " [\"" + String.join("\", \"", command) + "\"]";
        }
    }

    private interface ItemJoiner {
        String join(Map<String, String> map);
    }

    private static class MultiItemJoiner implements ItemJoiner {
        @Override
        public String join(Map<String, String> map) {
            return map.entrySet().stream().map(entry -> {
                String key = ItemJoinerUtil.isUnquotedStringWithWhitespaces(entry.getKey()) ? ItemJoinerUtil.toQuotedString(entry.getKey()) : entry.getKey();
                String value = ItemJoinerUtil.isUnquotedStringWithWhitespaces(entry.getValue()) ? ItemJoinerUtil.toQuotedString(entry.getValue()) : entry.getValue();
                value = value.replaceAll("(\r)*\n", "\\\\\n");
                return key + "=" + value;
            }).collect(Collectors.joining(" "));
        }
    }

    private static class ItemJoinerUtil {
        protected static boolean isUnquotedStringWithWhitespaces(String str) {
            return !str.matches("[\"].*[\"]") && str.matches(".*(?: |(?:\r?\n)).*");
        }

        protected static String toQuotedString(final String str) {
            return "\"".concat(str.replaceAll("\"", "\\\\\"")).concat("\"");
        }
    }

    /**
     * An instruction whose value is a Map.
     */
    public static abstract class MapInstruction implements Instruction {
        private final Provider<Map<String, String>> commandProvider;
        private final ItemJoiner joiner;

        public MapInstruction(Map<String, String> command) {
            this.commandProvider = Providers.ofNullable(command);
            this.joiner = new MultiItemJoiner();
        }

        public MapInstruction(Provider<Map<String, String>> commandProvider) {
            this.commandProvider = commandProvider;
            joiner = new MultiItemJoiner();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            final Map<String, String> evaluatedCommand = commandProvider.getOrNull();

            if (evaluatedCommand == null) {
                throw new IllegalArgumentException("instruction has to be set for " + getKeyword());
            }

            validateKeysAreNotBlank(evaluatedCommand);
            return getKeyword() + " " + joiner.join(evaluatedCommand);
        }

        private void validateKeysAreNotBlank(Map<String, String> command) throws IllegalArgumentException {
            command.entrySet().forEach(entry -> {
                if (entry.getKey().trim().isEmpty()) {
                    throw new IllegalArgumentException("blank keys for a key=value pair are not allowed: please check instruction " + getKeyword() + " and given pair `" + String.valueOf(entry) + "`");
                }
            });
        }
    }

    /**
     * An instruction whose value is a Dockerfile.File.
     */
    public static abstract class FileInstruction<T extends File> implements Instruction {
        private final Provider<T> provider;

        public FileInstruction(T file) {
            this.provider = Providers.ofNullable(file);
        }

        public FileInstruction(Provider<T> provider) {
            this.provider = provider;
        }

        @Internal
        public T getFile() {
            return provider.getOrNull();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            File fileValue = getFile();

            if (fileValue != null) {
                StringBuilder instruction = new StringBuilder(getKeyword());

                if (fileValue.getChown() != null) {
                    instruction.append(" --chown=" + fileValue.getChown());
                }

                if (fileValue.getSrc() != null && fileValue.getDest() != null) {
                    instruction.append(" " + fileValue.getSrc() + " " + fileValue.getDest());
                }

                return instruction.toString();
            }

            return null;
        }
    }

    /**
     * Represents a {@code FROM} instruction.
     */
    public static class FromInstruction implements Instruction {
        public static final String KEYWORD = "FROM";
        private final Provider<From> provider;

        public FromInstruction(From from) {
            this.provider = Providers.ofNullable(from);
        }

        public FromInstruction(Provider<From> provider) {
            this.provider = provider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            return buildTextInstruction(provider.getOrNull());
        }

        private String buildTextInstruction(From from) {
            if (from != null) {
                String result = getKeyword();

                if (from.getPlatform() != null) {
                    result += " --platform=" + from.getPlatform();
                }

                result += " " + from.getImage();

                if (from.getStage() != null) {
                    result += " AS " + from.getStage();
                }

                return result;
            }

            return null;
        }
    }

    /**
     * Represents a {@code ARG} instruction.
     */
    public static class ArgInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "ARG";

        public ArgInstruction(String arg) {
            super(arg);
        }

        public ArgInstruction(Provider<String> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code RUN} instruction.
     */
    public static class RunCommandInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "RUN";

        public RunCommandInstruction(String command) {
            super(command);
        }

        public RunCommandInstruction(Provider<String> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code CMD} instruction.
     */
    public static class DefaultCommandInstruction extends StringArrayInstruction {
        public static final String KEYWORD = "CMD";

        public DefaultCommandInstruction(String... command) {
            super(command);
        }

        public DefaultCommandInstruction(Provider<List<String>> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code EXPOSE} instruction.
     */
    public static class ExposePortInstruction implements Instruction {
        public static final String KEYWORD = "EXPOSE";
        private final Provider<List<Integer>> provider;

        public ExposePortInstruction(Integer... ports) {
            this.provider = Providers.ofNullable(new ArrayList<>(List.of(ports)));
        }

        public ExposePortInstruction(Provider<List<Integer>> provider) {
            this.provider = provider;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getText() {
            List<Integer> evaluatedPorts = provider.getOrNull();

            if (evaluatedPorts != null && !evaluatedPorts.isEmpty()) {
                return buildText(evaluatedPorts);
            }

            return null;
        }

        private String buildText(final List<Integer> ports) {
            return getKeyword() + " " + ports.stream().map(Object::toString).collect(Collectors.joining(" "));
        }
    }

    /**
     * Represents a {@code ENV} instruction.
     */
    public static class EnvironmentVariableInstruction extends MapInstruction {
        public static final String KEYWORD = "ENV";

        public EnvironmentVariableInstruction(String key, String value) {
            super(new HashMap<>(Map.of(key, value)));
        }

        public EnvironmentVariableInstruction(Map<String, String> envVars) {
            super(envVars);
        }

        public EnvironmentVariableInstruction(Provider<Map<String, String>> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code ADD} instruction.
     */
    public static class AddFileInstruction extends FileInstruction<File> {
        public static final String KEYWORD = "ADD";

        public AddFileInstruction(File file) {
            super(file);
        }

        public AddFileInstruction(Provider<File> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code COPY} instruction.
     */
    public static class CopyFileInstruction extends FileInstruction<CopyFile> {
        public static final String KEYWORD = "COPY";

        public CopyFileInstruction(CopyFile file) {
            super(file);
        }

        public CopyFileInstruction(Provider<CopyFile> provider) {
            super(provider);
        }

        @Override
        public String getText() {
            String text = super.getText();

            if (getFile() != null && getFile().getStage() != null) {
                int keywordIndex = getKeyword().length();
                text = text.substring(0, keywordIndex) + " --from=" + getFile().getStage() + text.substring(keywordIndex, text.length());
            }


            return text;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code ENTRYPOINT} instruction.
     */
    public static class EntryPointInstruction extends StringArrayInstruction {
        public static final String KEYWORD = "ENTRYPOINT";

        public EntryPointInstruction(String... entryPoint) {
            super(entryPoint);
        }

        public EntryPointInstruction(Provider<List<String>> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    public static class VolumeInstruction extends StringArrayInstruction {
        public static final String KEYWORD = "VOLUME";

        public VolumeInstruction(String... volume) {
            super(volume);
        }

        public VolumeInstruction(Provider<List<String>> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code USER} instruction.
     */
    public static class UserInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "USER";

        public UserInstruction(String user) {
            super(user);
        }

        public UserInstruction(Provider<String> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code WORKDIR} instruction.
     */
    public static class WorkDirInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "WORKDIR";

        public WorkDirInstruction(String dir) {
            super(dir);
        }

        public WorkDirInstruction(Provider<String> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code ONBUILD} instruction.
     */
    public static class OnBuildInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "ONBUILD";

        public OnBuildInstruction(String instruction) {
            super(instruction);
        }

        public OnBuildInstruction(Provider<String> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a {@code LABEL} instruction.
     */
    public static class LabelInstruction extends MapInstruction {
        public static final String KEYWORD = "LABEL";

        public LabelInstruction(Map labels) {
            super(labels);
        }

        public LabelInstruction(Provider<Map<String, String>> provider) {
            super(provider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    /**
     * Represents a comment instruction.
     *
     * @since 4.0.1
     */
    public static class CommentInstruction extends StringCommandInstruction {
        public static final String KEYWORD = "#";

        public CommentInstruction(String command) {
            super(command);
        }

        public CommentInstruction(Provider<String> commandProvider) {
            super(commandProvider);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword() {
            return KEYWORD;
        }
    }

    public static class HealthcheckInstruction implements Instruction {

        public static final String KEYWORD = "HEALTHCHECK";

        private final Provider<Healthcheck> provider;

        public HealthcheckInstruction(Healthcheck healthcheck) {
            this.provider = Providers.ofNullable(healthcheck);
        }

        public HealthcheckInstruction(Provider<Healthcheck> provider) {
            this.provider = provider;
        }

        @Nullable
        @Override
        public String getKeyword() {
            return KEYWORD;
        }

        @Nullable
        @Override
        public String getText() {
            return buildTextInstruction(provider.getOrNull());
        }

        private String buildTextInstruction(Healthcheck healthcheck) {
            if (healthcheck != null) {
                StringBuilder result = new StringBuilder(getKeyword());
                if (healthcheck.getInterval() != null) {
                    result.append(" --interval=").append(healthcheck.getInterval().toSeconds()).append("s");
                }
                if (healthcheck.getTimeout() != null) {
                    result.append(" --timeout=").append(healthcheck.getTimeout().toSeconds()).append("s");
                }
                if (healthcheck.getStartPeriod() != null) {
                    result.append(" --start-period=").append(healthcheck.getStartPeriod().toSeconds()).append("s");
                }
                if (healthcheck.getStartInterval() != null) {
                    result.append(" --start-interval=").append(healthcheck.getStartInterval().toSeconds()).append("s");
                }

                if (healthcheck.getRetries() != null) {
                    result.append(" --retries=").append(healthcheck.getRetries());
                }

                result.append(" CMD ").append(healthcheck.getCmd());
                return result.toString();
            }
            return null;
        }
    }

    /**
     * Input data for a {@link AddFileInstruction} or {@link CopyFileInstruction}.
     *
     * @since 4.0.0
     */
    public static class File {
        private final String src;
        private final String dest;
        @Nullable
        private String chown;

        public File(String src, String dest) {
            this.src = src;
            this.dest = dest;
        }

        /**
         * Specifies a given username, groupname, or UID/GID combination to request specific ownership of the copied content with the help of the {@code --chown} option.
         * <p>
         * Should be provided in the form of {@code <user>:<group>}.
         *
         * @param chown The ownership of the copied content
         */
        public File withChown(String chown) {
            this.chown = chown;
            return this;
        }

        /**
         * Return the source path.
         *
         * @return The source path
         */
        public String getSrc() {
            return src;
        }

        /**
         * Returns the destination path.
         *
         * @return The destination path
         */
        public String getDest() {
            return dest;
        }

        /**
         * Returns the ownership of the copied content.
         *
         * @return The ownership of the copied content
         */
        @Nullable
        public String getChown() {
            return chown;
        }
    }

    /**
     * Input data for a {@link CopyFileInstruction}.
     *
     * @since 5.0.0
     */
    public static class CopyFile extends File {
        public CopyFile(String src, String dest) {
            super(src, dest);
        }

        /**
         * Used to set the source location to a previous build stage.
         *
         * @param stage The previous stage
         * @return This instruction
         */
        public CopyFile withStage(String stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Returns the previous build stage.
         *
         * @return The previous stage
         */
        @Nullable
        public String getStage() {
            return stage;
        }

        @Nullable
        private String stage;
    }

    /**
     * Input data for a {@link FromInstruction}.
     *
     * @since 4.0.0
     */
    public static class From {
        private final String image;
        @Nullable
        private String stage;

        @Nullable
        private String platform;

        public From(String image) {
            this.image = image;
        }

        /**
         * Sets a new build stage by adding {@code AS} name to the {@code FROM} instruction.
         *
         * @param stage The stage
         * @return This instruction
         */
        public From withStage(String stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Sets the platform by adding {@code --platform} to the {@code FROM} instruction.
         *
         * @param platform The platform
         * @return This instruction
         */
        public From withPlatform(String platform) {
            this.platform = platform;
            return this;
        }

        /**
         * Returns the base image.
         *
         * @return The base image
         */
        public String getImage() {
            return image;
        }

        /**
         * Returns the stage.
         *
         * @return The stage
         */
        @Nullable
        public String getStage() {
            return stage;
        }

        /**
         * Returns the platform.
         *
         * @return The platform
         */
        @Nullable
        public String getPlatform() {
            return platform;
        }
    }

    /**
     * Input data for a {@link HealthcheckInstruction}.
     *
     * @see <a href="https://docs.docker.com/engine/reference/builder/#healthcheck">Dockerfile reference / HEALTHCHECK</a>.
     * @since 9.4.0
     */
    public static class Healthcheck {
        @Nullable
        private Duration interval;
        @Nullable
        private Duration timeout;
        @Nullable
        private Duration startPeriod;
        @Nullable
        private Duration startInterval = null;
        @Nullable
        private Integer retries;
        @Nonnull
        private final String cmd;

        public Healthcheck(@Nonnull String cmd) {
            this.cmd = cmd;
        }

        /**
         * Sets the healthcheck interval by adding {@code --interval} to Healthcheck instruction.
         *
         * @param interval a {@link Duration} in seconds.
         * @return this healthcheck.
         */
        public Healthcheck withInterval(Duration interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Sets the healthcheck timeout by adding {@code --timeout} to Healthcheck instruction.
         *
         * @param timeout a {@link Duration} in seconds.
         * @return this healthcheck.
         */
        public Healthcheck withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the healthcheck startPeriod by adding {@code --start-period} to Healthcheck instruction.
         *
         * @param startPeriod a {@link Duration} in seconds.
         * @return this healthcheck.
         */
        public Healthcheck withStartPeriod(Duration startPeriod) {
            this.startPeriod = startPeriod;
            return this;
        }

        /**
         * This option requires Docker Engine version 25.0 or later.
         * Sets the healthcheck startInterval by adding {@code --start-interval} to Healthcheck instruction.
         *
         * @param startInterval a {@link Duration} in seconds.
         * @return this healthcheck.
         */
        public Healthcheck withStartInterval(@Nullable Duration startInterval) {
            this.startInterval = startInterval;
            return this;
        }

        /**
         * Sets the healthcheck number of retries by adding {@code --retries} to Healthcheck instruction.
         *
         * @param retries the number of retries. Must be greater than 0, or it will fallback to the default (3).
         * @return this healthcheck.
         */
        public Healthcheck withRetries(int retries) {
            this.retries = retries;
            return this;
        }

        @Nullable
        public Duration getInterval() {
            return interval;
        }

        @Nullable
        public Duration getTimeout() {
            return timeout;
        }

        @Nullable
        public Duration getStartPeriod() {
            return startPeriod;
        }

        @Nullable
        public Duration getStartInterval() {
            return startInterval;
        }

        @Nullable
        public Integer getRetries() {
            return retries;
        }

        public String getCmd() {
            return cmd;
        }
    }
}
