package com.bmuschko.gradle.docker.tasks.image

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class Dockerfile extends DefaultTask {
    private final DockerfileBuilder builder = new DockerfileBuilder()

    @OutputFile
    File destFile = project.file("$project.buildDir/docker/Dockerfile")

    @TaskAction
    void create() {
        builder.build()
    }

    void from(String image) {
        builder.withFrom(image)
    }

    void maintainer(String maintainer) {
        builder.withMaintainer(maintainer)
    }

    void runCommand(String command) {
        builder.withRunCommand(command)
    }

    void defaultCommand(String... command) {
        builder.withDefaultCommand(command)
    }

    void exposePort(Integer port) {
        builder.withExposePort(port)
    }

    void environmentVariable(String key, String value) {
        builder.withEnvironmentVariable(key, value)
    }

    void addFile(String src, String dest) {
        builder.withAddedFile(src, dest)
    }

    void copyFile(String src, String dest) {
        builder.withCopiedFile(src, dest)
    }

    void entryPoint(String... entryPoint) {
        builder.withEntryPoint(entryPoint)
    }

    void volume(String... volume) {
        builder.withVolume(volume)
    }

    void user(String user) {
        builder.withUser(user)
    }

    void workingDir(String dir) {
        builder.withWorkingDir(dir)
    }

    void onBuild(String instruction) {
        builder.withOnBuild(instruction)
    }

    List<String> getInstructions() {
        builder.instructions
    }

    private class DockerfileBuilder {
        private final List<String> instructions = []

        void withFrom(String image) {
            instructions << "FROM $image"
        }

        void withMaintainer(String maintainer) {
            instructions << "MAINTAINER $maintainer"
        }

        void withRunCommand(String runCommand) {
            instructions << "RUN $runCommand"
        }

        void withDefaultCommand(String... defaultCommand) {
            instructions << 'CMD ["' + defaultCommand.join('", "') + '"]'
        }

        void withExposePort(Integer exposePort) {
            instructions << "EXPOSE $exposePort"
        }

        void withEnvironmentVariable(String key, String value) {
            instructions << "ENV $key $value"
        }

        void withAddedFile(String src, String dest) {
            instructions << "ADD $src $dest"
        }

        void withCopiedFile(String src, String dest) {
            instructions << "COPY $src $dest"
        }

        void withEntryPoint(String... entryPoint) {
            instructions << 'ENTRYPOINT ["' + entryPoint.join('", "') + '"]'
        }

        void withVolume(String... volume) {
            instructions << 'VOLUME ["' + volume.join('", "') + '"]'
        }

        void withUser(String user) {
            instructions << "USER $user"
        }

        void withWorkingDir(String workingDir) {
            instructions << "WORKDIR $workingDir"
        }

        void withOnBuild(String instruction) {
            instructions << "ONBUILD $instruction"
        }

        void build() {
            verifyValidInstructions()

            getDestFile().withWriter { out ->
                instructions.each { instruction ->
                    out.println instruction
                }
            }
        }

        private void verifyValidInstructions() {
            if(instructions.empty) {
                throw new IllegalStateException('Please specify instructions for your Dockerfile')
            }

            if(!instructions[0].startsWith('FROM')) {
                throw new IllegalStateException('The first instruction of a Dockerfile has to be FROM')
            }
        }

        List<String> getInstructions() {
            instructions
        }
    }
}
