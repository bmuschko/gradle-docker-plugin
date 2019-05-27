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
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
import com.bmuschko.gradle.docker.utils.OutputCollector
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.BuildImageCmd
import com.github.dockerjava.api.command.InspectImageCmd
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.AuthConfigurations
import com.github.dockerjava.api.model.BuildResponseItem
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.core.command.BuildImageResultCallback
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputFile

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
     * Tags for image.
     */
    @Input
    @Optional
    final SetProperty<String> tags = project.objects.setProperty(String)

    @Input
    @Optional
    final Property<Boolean> noCache = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> remove = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> quiet = project.objects.property(Boolean)

    @Input
    @Optional
    final Property<Boolean> pull = project.objects.property(Boolean)

    @Input
    @Optional
    final MapProperty<String, String> labels = project.objects.mapProperty(String, String)

    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    @Input
    @Optional
    final MapProperty<String, String> buildArgs = project.objects.mapProperty(String, String)

    @Input
    @Optional
    final SetProperty<String> cacheFrom = project.objects.setProperty(String)

    /**
     * Size of <code>/dev/shm</code> in bytes.
     * The size must be greater than 0.
     * If omitted the system uses 64MB.
     */
    @Input
    @Optional
    final Property<Long> shmSize = project.objects.property(Long)

    /**
     * With this parameter it is possible to build a special
     * stage in a multi stage Docker file. This is available with Docker 17.05
     * @since 4.10.0
     */
    @Input
    @Optional
    final Property<String> target = project.objects.property(String)

    /**
     * {@inheritDoc}
     */
    DockerRegistryCredentials registryCredentials

    /**
     * Output file containing the image ID of the built image. 
     * Defaults to "$buildDir/.docker/$taskpath-imageId.txt".
     * If path contains ':' it will be replaced by '_'.
     * @since 4.9.0
     */
    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty imageIdFile = newOutputFile()

    @Internal
    final Property<String> imageId = project.objects.property(String)

    DockerBuildImage() {
        inputDir.set(project.layout.buildDirectory.dir('docker'))
        tags.empty()
        noCache.set(false)
        remove.set(false)
        quiet.set(false)
        pull.set(false)
        cacheFrom.empty()
        String safeTaskPath = path.replaceFirst("^:", "").replaceAll(":", "_")
        imageIdFile.set(project.layout.buildDirectory.file(".docker/${safeTaskPath}-imageId.txt"))

        outputs.upToDateWhen {
            File file = imageIdFile.get().asFile
            if(file.exists()) {
                try {
                    getDockerClient().inspectImageCmd(file.text).exec()
                    return true
                } catch (DockerException e) {
                    return false
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

        if (tags.getOrNull()) {
            def tagListString = tags.get().collect {"'${it}'"}.join(", ")
            logger.quiet "Using tags ${tagListString} for image."
            buildImageCmd.withTags(tags.get().collect { it.toString() }.toSet() )
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
            buildImageCmd.withLabels(labels.get().collectEntries { [it.key, it.value.toString()] })
        }

        if(shmSize.getOrNull() != null) { // 0 is valid input
            buildImageCmd.withShmsize(shmSize.get())
        }

        if(target.getOrNull() != null) {
            buildImageCmd.withTarget(target.get())
        }

        if (registryCredentials) {
            AuthConfig authConfig = new AuthConfig()
            authConfig.registryAddress = registryCredentials.url.get()

            if (registryCredentials.username.isPresent()) {
                authConfig.withUsername(registryCredentials.username.get())
            }

            if (registryCredentials.password.isPresent()) {
                authConfig.withPassword(registryCredentials.password.get())
            }

            if (registryCredentials.email.isPresent()) {
                authConfig.withEmail(registryCredentials.email.get())
            }

            AuthConfigurations authConfigurations = new AuthConfigurations()
            authConfigurations.addConfig(authConfig)
            buildImageCmd.withBuildAuthConfigs(authConfigurations)
        }

        if (buildArgs.getOrNull()) {
            buildArgs.get().each { arg, value ->
                buildImageCmd = buildImageCmd.withBuildArg(arg, value)
            }
        }

        if (cacheFrom.getOrNull()) {
            buildImageCmd = buildImageCmd.withCacheFrom(cacheFrom.get())
        }

        String createdImageId = buildImageCmd.exec(createCallback()).awaitImageId()
        imageId.set(createdImageId)
        imageIdFile.get().asFile.text = createdImageId
        logger.quiet "Created image with ID '$createdImageId'."
    }

    private BuildImageResultCallback createCallback() {
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

            def collector = new OutputCollector({ s -> logger.quiet(s) })

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
}
