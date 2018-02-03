package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.utils.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The image repository.
     */
    @Input
    String repository

    /**
     * The image's tag.
     */
    @Input
    @Optional
    String tag

    /**
     * Where to save image. Can't be null.
     */
    @OutputFile
    File destFile

    @Override
    void runRemoteCommand(Object dockerClient) {
        if (!destFile || !destFile.isFile()) {
            throw new GradleException("Invalid destination file: " + destFile)
        }
        def saveImageCmd = dockerClient.saveImageCmd(getRepository())

        if (getTag()) {
            saveImageCmd.withTag(getTag())
        }
        InputStream image = saveImageCmd.exec()
        FileOutputStream fs = new FileOutputStream(destFile)
        try {
            IOUtils.copy(image, fs)
        } catch (IOException e) {
            throw new GradleException("Can't save image.", e)
        } finally {
            IOUtils.closeQuietly(image)
        }
    }
}
