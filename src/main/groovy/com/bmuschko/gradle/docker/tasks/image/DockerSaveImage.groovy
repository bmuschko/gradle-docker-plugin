package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.internal.IOUtils
import com.github.dockerjava.api.command.SaveImageCmd
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import java.util.zip.GZIPOutputStream

@CompileStatic
class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The image including repository, image name and tag to be saved e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    final Property<String> image = project.objects.property(String)

    @Input
    @Optional
    final Property<Boolean> useCompression = project.objects.property(Boolean)

    /**
     * Where to save image.
     */
    @OutputFile
    final RegularFileProperty destFile = project.objects.fileProperty()

    DockerSaveImage() {
        useCompression.set(false)
    }

    @Override
    void runRemoteCommand() {
        SaveImageCmd saveImageCmd = dockerClient.saveImageCmd(image.get())
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
