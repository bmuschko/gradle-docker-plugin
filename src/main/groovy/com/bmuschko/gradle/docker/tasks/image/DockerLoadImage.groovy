package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import org.gradle.api.tasks.Input

class DockerLoadImage extends AbstractDockerRemoteApiTask {

    @Input
    Closure<InputStream> imageStream

    @Override
    void runRemoteCommand(Object dockerClient) {
        dockerClient.loadImageCmd(imageStream()).exec()
    }
}
