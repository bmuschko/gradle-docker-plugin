package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.utils.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import java.util.zip.GZIPOutputStream

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

    @Input
    @Optional
    boolean useCompression

    /**
     * Where to save image.
     */
    @OutputFile
    File destFile

    @Override
    void runRemoteCommand(Object dockerClient) {
        def saveImageCmd = dockerClient.saveImageCmd(getRepository())

        if (getTag()) {
            saveImageCmd.withTag(getTag())
        }
        InputStream image = saveImageCmd.exec()
        OutputStream os
        try {
            FileOutputStream fs = new FileOutputStream(destFile)
            os = fs
            if( useCompression ) {
                os = new GZIPOutputStream(fs)
            }
            try {
                IOUtils.copy(image, os)
            } catch (IOException e) {
                throw new GradleException("Can't save image.", e)
            } finally {
                IOUtils.closeQuietly(image)
            }
        }
        finally {
            IOUtils.closeQuietly(os)
        }
    }
}
