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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class Dockerfile extends DefaultTask {
    @Nested
    List<Instruction> instructions = new ArrayList<Instruction>()

    @OutputFile
    File destFile = project.file("$project.buildDir/docker/Dockerfile")

    @TaskAction
    void create() {
        verifyValidInstructions()

        getDestFile().withWriter { out ->
            getInstructions().each { instruction ->
                out.println instruction.build()
            }
        }
    }

    private void verifyValidInstructions() {
        if(getInstructions().empty) {
            throw new IllegalStateException('Please specify instructions for your Dockerfile')
        }

        if(!getInstructions()[0].build().startsWith('FROM')) {
            throw new IllegalStateException('The first instruction of a Dockerfile has to be FROM')
        }
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#from">FROM instruction</a> sets the Base Image for
     * subsequent instructions.
     *
     * @param image Base image name
     */
    void from(String image) {
        getInstructions() << new FromInstruction(image)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#from">FROM instruction</a> sets the Base Image for
     * subsequent instructions.
     *
     * @param image Base image name
     */
    void from(Closure image) {
        getInstructions() << new FromInstruction(image)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#maintainer">MAINTAINER instruction</a> allows you to set
     * the Author field of the generated images.
     *
     * @param maintainer Maintainer
     */
    void maintainer(String maintainer) {
        getInstructions() << new MaintainerInstruction(maintainer)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#maintainer">MAINTAINER instruction</a> allows you to set
     * the Author field of the generated images.
     *
     * @param maintainer Maintainer
     */
    void maintainer(Closure maintainer) {
        getInstructions() << new MaintainerInstruction(maintainer)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#run">RUN instruction</a> will execute any commands in a
     * new layer on top of the current image and commit the results.
     *
     * @param command Command
     */
    void runCommand(String command) {
        getInstructions() << new RunCommandInstruction(command)
    }

    /**
     * The main purpose of a <a href="https://docs.docker.com/reference/builder/#cmd">CMD instruction</a> is to provide
     * defaults for an executing container.
     *
     * @param command Command
     */
    void defaultCommand(String... command) {
        getInstructions() << new DefaultCommandInstruction(command)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#expose">EXPOSE instruction</a> informs Docker that the
     * container will listen on the specified network ports at runtime.
     *
     * @param port Port
     */
    void exposePort(Integer... port) {
        getInstructions() << new ExposePortInstruction(port)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#expose">EXPOSE instruction</a> informs Docker that the
     * container will listen on the specified network ports at runtime.
     *
     * @param port Port
     */
    void exposePort(Closure port) {
        getInstructions() << new ExposePortInstruction(port)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#env">ENV instruction</a> sets the environment variable
     * <key> to the value <value>. This value will be passed to all future RUN instructions.
     *
     * @param key Key
     * @param value Value
     */
    void environmentVariable(String key, String value) {
        getInstructions() << new EnvironmentVariableInstruction(key, value)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#add">ADD instruction</a> copies new files, directories
     * or remote file URLs from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * @param src Source file
     * @param dest Destination path
     */
    void addFile(String src, String dest) {
        getInstructions() << new AddFileInstruction(src, dest)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#add">ADD instruction</a> copies new files, directories
     * or remote file URLs from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * @param src Source file
     * @param dest Destination path
     */
    void addFile(Closure src, Closure dest) {
        getInstructions() << new AddFileInstruction(src, dest)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#copy">COPY instruction</a> copies new files or directories
     * from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * @param src Source file
     * @param dest Destination path
     */
    void copyFile(String src, String dest) {
        getInstructions() << new CopyFileInstruction(src, dest)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#copy">COPY instruction</a> copies new files or directories
     * from <src> and adds them to the filesystem of the container at the path <dest>.
     *
     * @param src Source file
     * @param dest Destination path
     */
    void copyFile(Closure src, Closure dest) {
        getInstructions() << new CopyFileInstruction(src, dest)
    }

    /**
     * An <a href="https://docs.docker.com/reference/builder/#copy">ENTRYPOINT</a> allows you to configure a container
     * that will run as an executable.
     *
     * @param entryPoint Entry point
     */
    void entryPoint(String... entryPoint) {
        getInstructions() << new EntryPointInstruction(entryPoint)
    }

    /**
     * An <a href="https://docs.docker.com/reference/builder/#entrypoint">ENTRYPOINT</a> allows you to configure a container
     * that will run as an executable.
     *
     * @param entryPoint Entry point
     */
    void entryPoint(Closure entryPoint) {
        getInstructions() << new EntryPointInstruction(entryPoint)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#volume">VOLUME instruction</a> will create a mount point
     * with the specified name and mark it as holding externally mounted volumes from native host or other containers.
     *
     * @param volume Volume
     */
    void volume(String... volume) {
        getInstructions() << new VolumeInstruction(volume)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#user">USER instruction</a> sets the user name or UID to
     * use when running the image and for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     *
     * @param user User
     */
    void user(String user) {
        getInstructions() << new UserInstruction(user)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#workdir">WORKDIR instruction</a> sets the working directory
     * for any RUN, CMD and ENTRYPOINT instructions that follow it in the Dockerfile.
     *
     * @param dir Directory
     */
    void workingDir(String dir) {
        getInstructions() << new WorkDirInstruction(dir)
    }

    /**
     * The <a href="https://docs.docker.com/reference/builder/#onbuild">ONBUILD instruction</a> adds to the image a
     * trigger instruction to be executed at a later time, when the image is used as the base for another build.
     *
     * @param instruction Instruction
     */
    void onBuild(String instruction) {
        getInstructions() << new OnBuildInstruction(instruction)
    }

    static interface Instruction {
        String getKeyword()
        String build()
    }

    static abstract class StringCommandInstruction implements Instruction {
        @Input
        final Object command

        StringCommandInstruction(String command) {
            this.command = command
        }

        StringCommandInstruction(Closure command) {
            this.command = command
        }

        @Override
        String build() {
            if(command instanceof String) {
                "$keyword $command"
            }
            else if(command instanceof Closure) {
                "$keyword ${command()}"
            }
        }
    }

    static abstract class StringArrayInstruction implements Instruction {
        @Input
        final Object command

        StringArrayInstruction(String... command) {
            this.command = command
        }

        StringArrayInstruction(Closure command) {
            this.command = command
        }

        @Override
        String build() {
            if(command instanceof String[]) {
                keyword + ' ["' + command.join('", "') + '"]'
            }
            else if(command instanceof Closure) {
                def evaluatedCommand = command()

                if(evaluatedCommand instanceof String) {
                    keyword + ' ["' + evaluatedCommand + '"]'
                }
                else {
                    keyword + ' ["' + command().join('", "') + '"]'
                }
            }
        }
    }

    static abstract class FileInstruction implements Instruction {
        @Input
        final Object src

        @Input
        final Object dest

        FileInstruction(String src, String dest) {
            this.src = src
            this.dest = dest
        }

        FileInstruction(Closure src, Closure dest) {
            this.src = src
            this.dest = dest
        }

        @Override
        String build() {
            if(src instanceof String && dest instanceof String) {
                "$keyword $src $dest"
            }
            else if(src instanceof Closure && dest instanceof Closure) {
                "$keyword ${src()} ${dest()}"
            }
        }
    }

    static class FromInstruction extends StringCommandInstruction {
        FromInstruction(String image) {
            super(image)
        }

        FromInstruction(Closure image) {
            super(image)
        }

        @Override
        String getKeyword() {
            "FROM"
        }
    }

    static class MaintainerInstruction extends StringCommandInstruction {
        MaintainerInstruction(String maintainer) {
            super(maintainer)
        }

        MaintainerInstruction(Closure maintainer) {
            super(maintainer)
        }

        @Override
        String getKeyword() {
            "MAINTAINER"
        }
    }

    static class RunCommandInstruction extends StringCommandInstruction {
       RunCommandInstruction(String command) {
            super(command)
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

        @Override
        String getKeyword() {
            "CMD"
        }
    }

    static class ExposePortInstruction implements Instruction {
        @Input
        final Object port

        ExposePortInstruction(Integer... port) {
            this.port = port
        }

        ExposePortInstruction(Closure port) {
            this.port = port
        }

        @Override
        String getKeyword() {
            "EXPOSE"
        }

        @Override
        String build() {
            if(port instanceof Integer[]) {
                "$keyword ${port.join(' ')}"
            }
            else if(port instanceof Closure) {
                "$keyword ${port()}"
            }
        }
    }

    static class EnvironmentVariableInstruction implements Instruction {
        @Input
        final String key

        @Input
        final String value

        EnvironmentVariableInstruction(String key, String value) {
            this.key = key
            this.value = value
        }

        @Override
        String getKeyword() {
            "ENV"
        }

        @Override
        String build() {
            "$keyword $key $value"
        }
    }

    static class AddFileInstruction extends FileInstruction {
        AddFileInstruction(String src, String dest) {
            super(src, dest)
        }

        AddFileInstruction(Closure src, Closure dest) {
            super(src, dest)
        }

        @Override
        String getKeyword() {
            "ADD"
        }
    }

    static class CopyFileInstruction extends FileInstruction {
        CopyFileInstruction(String src, String dest) {
            super(src, dest)
        }

        CopyFileInstruction(Closure src, Closure dest) {
            super(src, dest)
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

        EntryPointInstruction(Closure entryPoint) {
            super(entryPoint)
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

        @Override
        String getKeyword() {
            "VOLUME"
        }
    }

    static class UserInstruction extends StringCommandInstruction {
        UserInstruction(String user) {
            super(user)
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

        @Override
        String getKeyword() {
            "WORKDIR"
        }
    }

    static class OnBuildInstruction extends StringCommandInstruction {
        OnBuildInstruction(String instruction) {
            super(instruction)
        }

        @Override
        String getKeyword() {
            "ONBUILD"
        }
    }
}
