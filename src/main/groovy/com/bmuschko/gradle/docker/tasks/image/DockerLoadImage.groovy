package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.DockerClient
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input

class DockerLoadImage extends AbstractDockerRemoteApiTask {

    @Input
    final RegularFileProperty imageFile = newOutputFile()

    @Override
    void runRemoteCommand(DockerClient dockerClient) {
        dockerClient.loadImageCmd(new FileInputStream(imageFile.get().asFile)).exec()
    }
}
