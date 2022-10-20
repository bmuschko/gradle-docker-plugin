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
package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.internal.OutputCollector
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.github.dockerjava.api.command.BuildImageCmd
import com.github.dockerjava.api.command.BuildImageResultCallback
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.model.AuthConfigurations
import com.github.dockerjava.api.model.BuildResponseItem
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

import java.util.function.Consumer

@CompileStatic
class DockerBuildImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * Input directory containing the build context. Defaults to "$buildDir/docker".
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty inputDir = project.objects.directoryProperty()

    /**
     * The Dockerfile to use to build the image.  If null, will use 'Dockerfile' in the
     * build context, i.e. "$inputDir/Dockerfile".
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final RegularFileProperty dockerFile = project.objects.fileProperty()

    /**
     * The images including repository, image name and tag to be built e.g. {@code vieux/apache:2.0}.
     *
     * @since 6.0.0
     */
    @Input
    @Optional
    final SetProperty<String> images = project.objects.setProperty(String)

    /**
     * When {@code true}, do not use docker cache when building the image.
     */
    @Input
    @Optional
    final Property<Boolean> noCache = project.objects.property(Boolean)

    /**
     * When {@code true}, remove intermediate containers after a successful build.
     */
    @Input
    @Optional
    final Property<Boolean> remove = project.objects.property(Boolean)

    /**
     * When {@code true}, suppress the build output and print image ID on success.
     */
    @Input
    @Optional
    final Property<Boolean> quiet = project.objects.property(Boolean)

    /**
     * When {@code true}, always attempt to pull a newer version of the image.
     */
    @Input
    @Optional
    final Property<Boolean> pull = project.objects.property(Boolean)

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
     *     @Override
     *     @Internal
     *     MapProperty<String, String> getLabels() {
     *         super.getLabels()
     *     }
     * }
     * </pre>
     *
     * A use case for excluding the labels from the up-to-date check is if build author wants to set build
     * information as labels (date, version-control-revision).
     */
    @Input
    @Optional
    MapProperty<String, String> labels = project.objects.mapProperty(String, String)

    /**
     * Networking mode for the RUN instructions during build.
     */
    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    /**
     * Build-time variables to pass to the image build.
     */
    @Input
    @Optional
    final MapProperty<String, String> buildArgs = project.objects.mapProperty(String, String)

    /**
     * Images to consider as cache sources.
     */
    @Input
    @Optional
    final SetProperty<String> cacheFrom = project.objects.setProperty(String)

    /**
     * Size of {@code /dev/shm} in bytes.
     * The size must be greater than 0.
     * If omitted the system uses 64MB.
     */
    @Input
    @Optional
    final Property<Long> shmSize = project.objects.property(Long)

    /**
     * Memory allocated for build specified in bytes (no suffix is needed)
     *
     * @since 7.3.0
     */
    @Input
    @Optional
    final Property<Long> memory = project.objects.property(Long)

    /**
     * With this parameter it is possible to build a special stage in a multi-stage Docker file.
     * <p>
     * This feature is only available for use with Docker 17.05 and higher.
     *
     * @since 4.10.0
     */
    @Input
    @Optional
    final Property<String> target = project.objects.property(String)

    /**
     * Build-time additional host list to pass to the image build in the format {@code host:ip}.
     *
     * @since 6.2.0
     */
    @Input
    @Optional
    final SetProperty<String> extraHosts = project.objects.setProperty(String)

    /**
     * The target platform in the format {@code os[/arch[/variant]]}, for example {@code linux/s390x} or {@code darwin}.
     *
     * @since 7.1.0
     */
    @Input
    @Optional
    final Property<String> platform = project.objects.property(String)

    /**
     * {@inheritDoc}
     */
    final DockerRegistryCredentials registryCredentials

    /**
     * Output file containing the image ID of the built image.
     * Defaults to "$buildDir/.docker/$taskpath-imageId.txt".
     * If path contains ':' it will be replaced by '_'.
     *
     * @since 4.9.0
     */
    @OutputFile
    final RegularFileProperty imageIdFile = project.objects.fileProperty()

    /**
     * The ID of the image built. The value of this property requires the task action to be executed.
     */
    @Internal
    final Property<String> imageId = project.objects.property(String)

    DockerBuildImage() {
        inputDir.set(project.layout.buildDirectory.dir('docker'))
        images.empty()
        noCache.set(false)
        remove.set(false)
        quiet.set(false)
        pull.set(false)
        cacheFrom.empty()

        imageId.set(imageIdFile.map { RegularFile it ->
            File file = it.asFile
            if(file.exists()) {
                return file.text
            }
            return null
        })

        String safeTaskPath = path.replaceFirst("^:", "").replaceAll(":", "_")
        registryCredentials = project.objects.newInstance(DockerRegistryCredentials, project.objects)
        imageIdFile.set(project.layout.buildDirectory.file(".docker/${safeTaskPath}-imageId.txt"))

        outputs.upToDateWhen upToDateWhenSpec
    }

    private Spec<Task> upToDateWhenSpec = new Spec<Task>() {
        @Override
        boolean isSatisfiedBy(Task element) {
            File file = imageIdFile.get().asFile
            if(file.exists()) {
                try {
                    def fileImageId = file.text
                    def repoTags = dockerClient.inspectImageCmd(fileImageId).exec().repoTags
                    if (!images.present || repoTags.containsAll(images.get())) {
                        return true
                    }
                } catch (DockerException ignored) {
                }
            }
            return false
        }
    }

    @Override
    void runRemoteCommand() {
        logger.quiet "Building image using context '${inputDir.get().asFile}'."
        BuildImageCmd buildImageCmd

        if (dockerFile.getOrNull()) {
            logger.quiet "Using Dockerfile '${dockerFile.get().asFile}'"
            buildImageCmd = dockerClient.buildImageCmd()
                    .withBaseDirectory(inputDir.get().asFile)
                    .withDockerfile(dockerFile.get().asFile)
        } else {
            buildImageCmd = dockerClient.buildImageCmd(inputDir.get().asFile)
        }

        if (images.getOrNull()) {
            String tagListString = images.get().collect {"'${it}'"}.join(", ")
            logger.quiet "Using images ${tagListString}."
            buildImageCmd.withTags(images.get() as Set<String>)
        }

        if (noCache.getOrNull()) {
            buildImageCmd.withNoCache(noCache.get())
        }

        if (remove.getOrNull()) {
            buildImageCmd.withRemove(remove.get())
        }

        if (quiet.getOrNull()) {
            buildImageCmd.withQuiet(quiet.get())
        }

        if (pull.getOrNull()) {
            buildImageCmd.withPull(pull.get())
        }

        if (network.getOrNull()) {
            buildImageCmd.withNetworkMode(network.get())
        }

        if (labels.getOrNull()) {
            buildImageCmd.withLabels(labels.get())
        }

        if(shmSize.getOrNull() != null) { // 0 is valid input
            buildImageCmd.withShmsize(shmSize.get())
        }

        if(memory.getOrNull() != null) {
            buildImageCmd.withMemory(memory.get())
        }

        if(target.getOrNull() != null) {
            buildImageCmd.withTarget(target.get())
        }

        if(platform.getOrNull() != null) {
            buildImageCmd.withPlatform(platform.get())
        }

        AuthConfigurations authConfigurations = getRegistryAuthLocator().lookupAllAuthConfigs(registryCredentials)
        buildImageCmd.withBuildAuthConfigs(authConfigurations)

        if (buildArgs.getOrNull()) {
            for (Map.Entry<String, String> entry : buildArgs.get().entrySet()) {
                buildImageCmd.withBuildArg(entry.key, entry.value)
            }
        }

        if (cacheFrom.getOrNull()) {
            buildImageCmd.withCacheFrom(cacheFrom.get() as Set<String>)
        }

        if (extraHosts.getOrNull()) {
            buildImageCmd.withExtraHosts(extraHosts.get() as Set<String>)
        }

        String createdImageId = buildImageCmd.exec(createCallback(nextHandler)).awaitImageId()
        imageIdFile.get().asFile.parentFile.mkdirs()
        imageIdFile.get().asFile.text = createdImageId
        logger.quiet "Created image with ID '$createdImageId'."
    }

    private BuildImageResultCallback createCallback(Action nextHandler) {
        if (nextHandler) {
            return new BuildImageResultCallback() {
                @Override
                void onNext(BuildResponseItem item) {
                    try {
                        nextHandler.execute(item)
                    } catch (Exception e) {
                        logger.error('Failed to handle build response', e)
                        return
                    }
                    super.onNext(item)
                }
            }
        }

        new BuildImageResultCallback() {
            OutputCollector collector = new OutputCollector(new Consumer<String>() {
                @Override
                void accept(String s) {
                    logger.quiet(s)
                }
            })

            @Override
            void onNext(BuildResponseItem item) {
                try {
                    def possibleStream = item.stream
                    if (possibleStream) {
                        collector.accept(possibleStream)
                    }
                } catch(Exception e) {
                    logger.error('Failed to handle build response', e)
                    return
                }
                super.onNext(item)
            }

            @Override
            void close() throws IOException {
                collector.close()
                super.close()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials)
    }
}
