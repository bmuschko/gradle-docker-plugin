package com.bmuschko.gradle.docker.tasks.image;

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

import java.io.FileInputStream;
import java.io.IOException;

public class DockerLoadImage extends AbstractDockerRemoteApiTask {

    @InputFile
    public final RegularFileProperty getImageFile() {
        return imageFile;
    }

    private final RegularFileProperty imageFile = getProject().getObjects().fileProperty();

    @Override
    public void runRemoteCommand() throws IOException {
        getDockerClient().loadImageCmd(new FileInputStream(imageFile.get().getAsFile())).exec();
    }
}
