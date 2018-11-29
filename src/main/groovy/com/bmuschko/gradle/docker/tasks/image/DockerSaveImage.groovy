package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.utils.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import java.util.zip.GZIPOutputStream

class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The image repository.
     */
    @Input
    final Property<String> repository = project.objects.property(String)

    /**
     * The image's tag.
     */
    @Input
    @Optional
    final Property<String> tag = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> useCompression = project.objects.property(Boolean)

    /**
     * Where to save image.
     */
    @OutputFile
    final RegularFileProperty destFile = newOutputFile()

    DockerSaveImage() {
        useCompression.set(false)
    }

    @Override
    void runRemoteCommand(Object dockerClient) {
        def saveImageCmd = dockerClient.saveImageCmd(repository.get())

        if (tag.getOrNull()) {
            saveImageCmd.withTag(tag.get())
        }
        InputStream image = saveImageCmd.exec()
        OutputStream os
        try {
            FileOutputStream fs = new FileOutputStream(destFile.get().asFile)
            os = fs
            if( useCompression.get() ) {
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
