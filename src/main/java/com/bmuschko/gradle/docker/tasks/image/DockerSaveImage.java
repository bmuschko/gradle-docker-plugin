package com.bmuschko.gradle.docker.tasks.image;

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.github.dockerjava.api.command.SaveImagesCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.core.command.AbstrDockerCmd;
import com.github.dockerjava.core.command.SaveImagesCmdImpl;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class DockerSaveImage extends AbstractDockerRemoteApiTask {

    /**
     * The images including repository, image name and tag to be saved e.g. {@code vieux/apache:2.0}.
     *
     * @since 8.0.0
     */
    @Input
    public final SetProperty<String> getImages() {
        return images;
    }

    private final SetProperty<String> images = getProject().getObjects().setProperty(String.class);

    @Input
    @Optional
    public final Property<Boolean> getUseCompression() {
        return useCompression;
    }

    private final Property<Boolean> useCompression = getProject().getObjects().property(Boolean.class);

    /**
     * Where to save image.
     */

    @OutputFile
    public final RegularFileProperty getDestFile() {
        return destFile;
    }

    private final RegularFileProperty destFile = getProject().getObjects().fileProperty();

    /**
     * Output file containing the image IDs of the saved images.
     * Defaults to "$buildDir/.docker/$taskpath-imageIds.properties".
     * If path contains ':' it will be replaced by '_'.
     *
     * @since 8.0.0
     */

    @OutputFile
    public final RegularFileProperty getImageIdsFile() {
        return imageIdsFile;
    }

    private final RegularFileProperty imageIdsFile = getProject().getObjects().fileProperty();

    private final Spec<Task> onlyIfSpec = new Spec<Task>() {
        @Override
        public boolean isSatisfiedBy(Task element) {
            return getImages().getOrNull() != null;
        }

    };
    private final Spec<Task> upToDateWhenSpec = new Spec<Task>() {
        @Override
        public boolean isSatisfiedBy(Task element) {
            File file = getImageIdsFile().get().getAsFile();
            if (file.exists()) {
                final Properties savedImageIds = new Properties();
                try (InputStream is = Files.newInputStream(file.toPath())) {
                    savedImageIds.load(is);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                Set<String> savedImages = savedImageIds.stringPropertyNames();

                Set<String> configuredImages = getImages().getOrElse(new HashSet<>());
                if (!savedImages.equals(configuredImages)) {
                    return false;
                }

                try {
                    for (String savedImage : savedImages) {
                        String savedId = savedImageIds.getProperty(savedImage);
                        if (!savedId.equals(getImageIds(savedImage))) {
                            return false;
                        }
                    }
                    return true;
                } catch (DockerException e) {
                    return false;
                }
            }
            return false;
        }
    };

    public DockerSaveImage() {
        useCompression.convention(false);
        final String safeTaskPath = getPath().replaceFirst("^:", "").replaceAll(":", "_");
        imageIdsFile.convention(getProject().getLayout().getBuildDirectory().file(".docker/" + safeTaskPath + "-imageIds.properties"));

        onlyIf(onlyIfSpec);

        getOutputs().upToDateWhen(upToDateWhenSpec);
    }

    // part of work-around for https://github.com/docker-java/docker-java/issues/1872
    private SaveImagesCmd.Exec getExecution() {
        try {
            Field execution = AbstrDockerCmd.class.getDeclaredField("execution");
            execution.setAccessible(true);
            return (SaveImagesCmd.Exec) execution.get(getDockerClient().saveImagesCmd());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void runRemoteCommand() {
        final Set<String> images = getImages().getOrElse(new HashSet<>());
        // part of work-around for https://github.com/docker-java/docker-java/issues/1872
        SaveImagesCmd saveImagesCmd = new SaveImagesCmdImpl(getExecution()) {
            @Override
            public List<TaggedImage> getImages() {
                return images.stream().map(it -> (TaggedImage) () -> it).collect(Collectors.toList());
            }
        };
        try (InputStream image = saveImagesCmd.exec();
                OutputStream fs = Files.newOutputStream(destFile.get().getAsFile().toPath());
            OutputStream os = useCompression.get() ? new GZIPOutputStream(fs) : fs) {
                image.transferTo(os);
        } catch (IOException e) {
            throw new GradleException("Can't save image.", e);
        }

        final Properties imageIds = new Properties();
        for (String configuredImage : images) {
            imageIds.put(configuredImage, getImageIds(configuredImage));
        }
        imageIdsFile.get().getAsFile().getParentFile().mkdirs();
        try (OutputStream os = Files.newOutputStream(imageIdsFile.get().getAsFile().toPath())) {
            imageIds.store(os, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getImageIds(String image) {
        if (image.contains(":")) {
            return getImageIdForConcreteImage(image);
        } else {
            return getImageIdsForBaseImage(image);
        }
    }

    private String getImageIdForConcreteImage(String image) {
        return getDockerClient().inspectImageCmd(image).exec().getId();
    }

    private String getImageIdsForBaseImage(final String image) {
        return getDockerClient()
                .listImagesCmd()
                .exec()
                .stream()
                .filter(listedImage -> listedImage.getRepoTags() != null)
                .flatMap(listedImage -> Arrays.stream(listedImage.getRepoTags()).map(it -> Map.entry(it, listedImage.getId())))
                .filter(i -> i.getKey().startsWith(image))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.joining(","));
    }
}
