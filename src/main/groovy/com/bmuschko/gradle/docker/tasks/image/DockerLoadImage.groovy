package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

class DockerLoadImage extends AbstractDockerRemoteApiTask {

    @Input
    String inputFilePath

    @Override
    void runRemoteCommand(Object dockerClient) {
        InputStream imageStream = new File(inputFilePath).newInputStream() 
        dockerClient.loadImageCmd(imageStream).exec()
    }
}
