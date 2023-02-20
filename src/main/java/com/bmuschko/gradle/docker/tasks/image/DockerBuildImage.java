/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks.image;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import com.bmuschko.gradle.docker.internal.OutputCollector;
import com.bmuschko.gradle.docker.internal.RegularFileToStringTransformer;
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.AuthConfigurations;
import com.github.dockerjava.api.model.BuildResponseItem;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DockerBuildImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * Input directory containing the build context. Defaults to "$buildDir/docker".
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public final DirectoryProperty getInputDir() {
        return inputDir;
    }

    private final DirectoryProperty inputDir = getProject().getObjects().directoryProperty();

    /**
     * The Dockerfile to use to build the image.  If null, will use 'Dockerfile' in the
     * build context, i.e. "$inputDir/Dockerfile".
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    public final RegularFileProperty getDockerFile() {
        return dockerFile;
    }

    private final RegularFileProperty dockerFile = getProject().getObjects().fileProperty();

    /**
     * The images including repository, image name and tag to be built e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    @Optional
    public final SetProperty<String> getImages() {
        return images;
    }

    private final SetProperty<String> images = getProject().getObjects().setProperty(String.class);

    /**
     * When {@code true}, do not use docker cache when building the image.
     */
    @Input
    @Optional
    public final Property<Boolean> getNoCache() {
        return noCache;
    }

    private final Property<Boolean> noCache = getProject().getObjects().property(Boolean.class);

    /**
     * When {@code true}, remove intermediate containers after a successful build.
     */
    @Input
    @Optional
    public final Property<Boolean> getRemove() {
        return remove;
    }

    private final Property<Boolean> remove = getProject().getObjects().property(Boolean.class);

    /**
     * When {@code true}, suppress the build output and print image ID on success.
     */
    @Input
    @Optional
    public final Property<Boolean> getQuiet() {
        return quiet;
    }

    private final Property<Boolean> quiet = getProject().getObjects().property(Boolean.class);

    /**
     * When {@code true}, always attempt to pull a newer version of the image.
     */
    @Input
    @Optional
    public final Property<Boolean> getPull() {
        return pull;
    }

    private final Property<Boolean> pull = getProject().getObjects().property(Boolean.class);

    /**
     * Labels to attach as metadata for to the image.
     * <p>
     * This property is not final to allow build authors to remove the labels from the up-to-date
     * check by extending {@code DockerBuildImage} and annotating the overrided {@code getLabels()} method
     * with {@code @Internal}, example:
     *
     * <pre>
     * import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
     *
     * class CustomDockerBuildImage extends DockerBuildImage {
     *    {@literal @}Override
     *    {@literal @}Internal
     *     MapProperty&lt;String, String&gt; getLabels() {
     *         super.getLabels()
     *     }
     * }
     * </pre>
     * <p>
     * A use case for excluding the labels from the up-to-date check is if build author wants to set build
     * information as labels (date, version-control-revision).
     */
    @Input
    @Optional
    public MapProperty<String, String> getLabels() {
        return labels;
    }

    private MapProperty<String, String> labels = getProject().getObjects().mapProperty(String.class, String.class);

    /**
     * Networking mode for the RUN instructions during build.
     */
    @Input
    @Optional
    public final Property<String> getNetwork() {
        return network;
    }

    private final Property<String> network = getProject().getObjects().property(String.class);

    /**
     * Build-time variables to pass to the image build.
     */
    @Input
    @Optional
    public final MapProperty<String, String> getBuildArgs() {
        return buildArgs;
    }

    private final MapProperty<String, String> buildArgs = getProject().getObjects().mapProperty(String.class, String.class);

    /**
     * Images to consider as cache sources.
     */
    @Input
    @Optional
    public final SetProperty<String> getCacheFrom() {
        return cacheFrom;
    }

    private final SetProperty<String> cacheFrom = getProject().getObjects().setProperty(String.class);

    /**
     * Size of {@code /dev/shm} in bytes.
     * The size must be greater than 0.
     * If omitted the system uses 64MB.
     */
    @Input
    @Optional
    public final Property<Long> getShmSize() {
        return shmSize;
    }

    private final Property<Long> shmSize = getProject().getObjects().property(Long.class);

    /**
     * Memory allocated for build specified in bytes (no suffix is needed)
     *
     * @since 7.3.0
     */
    @Input
    @Optional
    public final Property<Long> getMemory() {
        return memory;
    }

    private final Property<Long> memory = getProject().getObjects().property(Long.class);

    /**
     * With this parameter it is possible to build a special stage in a multi-stage Docker file.
     * <p>
     * This feature is only available for use with Docker 17.05 and higher.
     *
     * @since 4.10.0
     */
    @Input
    @Optional
    public final Property<String> getTarget() {
        return target;
    }

    private final Property<String> target = getProject().getObjects().property(String.class);

    /**
     * Build-time additional host list to pass to the image build in the format {@code host:ip}.
     *
     * @since 6.2.0
     */
    @Input
    @Optional
    public final SetProperty<String> getExtraHosts() {
        return extraHosts;
    }

    private final SetProperty<String> extraHosts = getProject().getObjects().setProperty(String.class);

    /**
     * The target platform in the format {@code os[/arch[/variant]]}, for example {@code linux/s390x} or {@code darwin}.
     *
     * @since 7.1.0
     */
    @Input
    @Optional
    public final Property<String> getPlatform() {
        return platform;
    }

    private final Property<String> platform = getProject().getObjects().property(String.class);

    /**
     * {@inheritDoc}
     */
    public final DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    private final DockerRegistryCredentials registryCredentials;

    /**
     * Output file containing the image ID of the built image.
     * Defaults to "$buildDir/.docker/$taskpath-imageId.txt".
     * If path contains ':' it will be replaced by '_'.
     *
     * @since 4.9.0
     */
    @OutputFile
    public final RegularFileProperty getImageIdFile() {
        return imageIdFile;
    }

    private final RegularFileProperty imageIdFile = getProject().getObjects().fileProperty();

    /**
     * The ID of the image built. The value of this property requires the task action to be executed.
     */
    @Internal
    public final Property<String> getImageId() {
        return imageId;
    }

    private final Property<String> imageId = getProject().getObjects().property(String.class);

    public DockerBuildImage() {
        inputDir.convention(getProject().getLayout().getBuildDirectory().dir("docker"));
        noCache.convention(false);
        remove.convention(false);
        quiet.convention(false);
        pull.convention(false);

        imageId.convention(imageIdFile.map(new RegularFileToStringTransformer()));

        final String safeTaskPath = getPath().replaceFirst("^:", "").replaceAll(":", "_");
        registryCredentials = getProject().getObjects().newInstance(DockerRegistryCredentials.class, getProject().getObjects());
        imageIdFile.convention(getProject().getLayout().getBuildDirectory().file(".docker/" + safeTaskPath + "-imageId.txt"));

        getOutputs().upToDateWhen(upToDateWhenSpec);
    }

    private final Spec<Task> upToDateWhenSpec = new Spec<Task>() {
        @Override
        public boolean isSatisfiedBy(Task element) {
            File file = getImageIdFile().get().getAsFile();
            if (file.exists()) {
                try {
                    String fileImageId;
                    try {
                        fileImageId = Files.readString(file.toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    List<String> repoTags = getDockerClient().inspectImageCmd(fileImageId).exec().getRepoTags();
                    if (!getImages().isPresent() || repoTags.containsAll(getImages().get())) {
                        return true;
                    }
                } catch (DockerException ignored) {
                }
            }
            return false;
        }
    };

    @Override
    public void runRemoteCommand() throws Exception {
        getLogger().quiet("Building image using context '" + getInputDir().get().getAsFile() + "'.");
        BuildImageCmd buildImageCmd;

        if (dockerFile.getOrNull() != null) {
            getLogger().quiet("Using Dockerfile '" + getDockerFile().get().getAsFile() + "'");
            buildImageCmd = getDockerClient().buildImageCmd()
                    .withBaseDirectory(inputDir.get().getAsFile())
                    .withDockerfile(dockerFile.get().getAsFile());
        } else {
            buildImageCmd = getDockerClient().buildImageCmd(inputDir.get().getAsFile());
        }

        if (images.getOrNull() != null && !images.get().isEmpty()) {
            final String tagListString = images.get().stream().map(it -> "'" + it + "'").collect(Collectors.joining(", "));
            getLogger().quiet("Using images " + tagListString + ".");
            buildImageCmd.withTags(images.get());
        }

        if (Boolean.TRUE.equals(noCache.getOrNull())) {
            buildImageCmd.withNoCache(noCache.get());
        }

        if (Boolean.TRUE.equals(remove.getOrNull())) {
            buildImageCmd.withRemove(remove.get());
        }

        if (Boolean.TRUE.equals(quiet.getOrNull())) {
            buildImageCmd.withQuiet(quiet.get());
        }

        if (Boolean.TRUE.equals(pull.getOrNull())) {
            buildImageCmd.withPull(pull.get());
        }

        if (network.getOrNull() != null) {
            buildImageCmd.withNetworkMode(network.get());
        }

        if (labels.getOrNull() != null && !labels.get().isEmpty()) {
            buildImageCmd.withLabels(labels.get());
        }

        if(shmSize.getOrNull() != null) { // 0 is valid input
            buildImageCmd.withShmsize(shmSize.get());
        }

        if(memory.getOrNull() != null) {
            buildImageCmd.withMemory(memory.get());
        }

        if(target.getOrNull() != null) {
            buildImageCmd.withTarget(target.get());
        }

        if(platform.getOrNull() != null) {
            buildImageCmd.withPlatform(platform.get());
        }

        AuthConfigurations authConfigurations = getRegistryAuthLocator().lookupAllAuthConfigs(registryCredentials);
        buildImageCmd.withBuildAuthConfigs(authConfigurations);

        if (buildArgs.getOrNull() != null && !buildArgs.get().isEmpty()) {
            for (Map.Entry<String, String> entry : buildArgs.get().entrySet()) {
                buildImageCmd.withBuildArg(entry.getKey(), entry.getValue());
            }
        }

        if (cacheFrom.getOrNull() != null && !cacheFrom.get().isEmpty()) {
            buildImageCmd.withCacheFrom(cacheFrom.get());
        }

        if (extraHosts.getOrNull() != null && !extraHosts.get().isEmpty()) {
            buildImageCmd.withExtraHosts(extraHosts.get());
        }

        String createdImageId = buildImageCmd.exec(createCallback(getNextHandler())).awaitImageId();
        imageIdFile.get().getAsFile().getParentFile().mkdirs();
        Files.writeString(imageIdFile.get().getAsFile().toPath(), createdImageId);
        getLogger().quiet("Created image with ID '" + createdImageId + "'.");
    }

    private BuildImageResultCallback createCallback(final Action<BuildResponseItem> nextHandler) {
        if (nextHandler != null) {
            return new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    try {
                        nextHandler.execute(item);
                    } catch (Exception e) {
                        getLogger().error("Failed to handle build response", e);
                        return;
                    }
                    super.onNext(item);
                }
            };
        }

        return new BuildImageResultCallback() {
            private final OutputCollector collector = new OutputCollector(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    getLogger().quiet(s);
                }
            });

            @Override
            public void onNext(BuildResponseItem item) {
                try {
                    String possibleStream = item.getStream();
                    if (possibleStream != null && !possibleStream.isEmpty()) {
                        collector.accept(possibleStream);
                    }
                } catch (Exception e) {
                    getLogger().error("Failed to handle build response", e);
                    return;
                }
                super.onNext(item);
            }

            @Override
            public void close() throws IOException {
                collector.close();
                super.close();
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials);
    }
}
