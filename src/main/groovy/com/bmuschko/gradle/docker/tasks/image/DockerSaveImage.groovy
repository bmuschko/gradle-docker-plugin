package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.internal.IOUtils
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.github.dockerjava.api.command.SaveImagesCmd
import com.github.dockerjava.api.command.SaveImagesCmd.TaggedImage
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.core.command.SaveImagesCmdImpl
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import java.util.zip.GZIPOutputStream

@CompileStatic
class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The images including repository, image name and tag to be saved e.g. {@code vieux/apache:2.0}.
     *
     * @since 8.0.0
     */
    @Input
    final SetProperty<String> images = project.objects.setProperty(String)

    @Input
    @Optional
    final Property<Boolean> useCompression = project.objects.property(Boolean)

    /**
     * Where to save image.
     */

    @OutputFile
    final RegularFileProperty destFile = project.objects.fileProperty()

    /**
     * Output file containing the image IDs of the saved images.
     * Defaults to "$buildDir/.docker/$taskpath-imageIds.properties".
     * If path contains ':' it will be replaced by '_'.
     *
     * @since 8.0.0
     */

    @OutputFile
    final RegularFileProperty imageIdsFile = project.objects.fileProperty()

    DockerSaveImage() {
        useCompression.set(false)
        String safeTaskPath = path.replaceFirst("^:", "").replaceAll(":", "_")
        imageIdsFile.set(project.layout.buildDirectory.file(".docker/${safeTaskPath}-imageIds.properties"))

        onlyIf {
            images.getOrNull()
        }

        outputs.upToDateWhen {
            File file = imageIdsFile.get().asFile
            if (file.exists()) {
                def savedImageIds = new Properties()
                file.withInputStream { savedImageIds.load(it) }
                def savedImages = savedImageIds.stringPropertyNames()

                Set<String> configuredImages = images.getOrElse([] as Set)
                if (savedImages != configuredImages) {
                    return false
                }

                try {
                    savedImages.each { savedImage ->
                        def savedId = savedImageIds.getProperty(savedImage)
                        if (savedId != getImageIds(savedImage)) {
                            return false
                        }
                    }
                    return true
                } catch (DockerException e) {
                    return false
                }
            }
            return false
        }
    }

    // part of work-around for https://github.com/docker-java/docker-java/issues/1872
    @CompileDynamic
    private SaveImagesCmd.Exec getExecution() {
        dockerClient.saveImagesCmd().@execution
    }

    @Override
    void runRemoteCommand() {
        Set<String> images = images.getOrElse([] as Set)
        // part of work-around for https://github.com/docker-java/docker-java/issues/1872
        SaveImagesCmd saveImagesCmd = new SaveImagesCmdImpl(execution) {
            @Override
            List<TaggedImage> getImages() {
                images.collect {
                    { -> it } as TaggedImage
                }
            }
        }

        saveImagesCmd.exec().withCloseable { InputStream image ->
            FileOutputStream fs = new FileOutputStream(destFile.get().asFile)
            (useCompression.get() ? new GZIPOutputStream(fs) : fs).withCloseable { OutputStream os ->
                try {
                    IOUtils.copy(image, os)
                } catch (IOException e) {
                    throw new GradleException("Can't save image.", e)
                }
            }
        }

        def imageIds = new Properties()
        images.each { configuredImage ->
            imageIds[configuredImage] = getImageIds(configuredImage)
        }
        imageIdsFile.get().asFile.parentFile.mkdirs()
        imageIdsFile.get().asFile.withOutputStream {
            imageIds.store(it, null)
        }
    }

    private getImageIds(String image) {
        if (image.contains(":")) {
            getImageIdForConcreteImage(image)
        } else {
            getImageIdsForBaseImage(image)
        }
    }

    private getImageIdForConcreteImage(String image) {
        dockerClient.inspectImageCmd(image).exec().id
    }

    private getImageIdsForBaseImage(String image) {
        dockerClient
            .listImagesCmd()
            .exec()
            .collectMany { listedImage ->
                listedImage
                    .repoTags
                    .collect { [it, listedImage.id] }
            }
            .collectEntries { it }
            .findAll { "$it.key".startsWith(image) }
            .toSorted { it.key }
            .collect { it.value }
            .join(",")
    }
}
