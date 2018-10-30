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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*

@CacheableTask
class DockerBuildImage extends AbstractDockerRemoteApiTask implements RegistryCredentialsAware {

    /**
     * Input directory containing the build context. Defaults to "$projectDir/docker".
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    final DirectoryProperty inputDir = newInputDirectory()

    /**
     * The Dockerfile to use to build the image.  If null, will use 'Dockerfile' in the
     * build context, i.e. "$inputDir/Dockerfile".
     */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    final RegularFileProperty dockerFile = newInputFile()

    /**
     * Tags for image.
     */
    @Input
    @Optional
    final SetProperty<String> tags = project.objects.setProperty(String)

    /**
     * Tag for image.
     * @deprecated use {@link #tags}
     */
    @Input
    @Optional
    @Deprecated
    final Property<String> tag = project.objects.property(String)

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
    final Property<Map<String, String>> labels = project.objects.property(Map)

    @Input
    @Optional
    final Property<String> network = project.objects.property(String)

    @Input
    @Optional
    final Property<Map<String, String>> buildArgs = project.objects.property(Map)

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
     * The target Docker registry credentials for building image.
     */
    @Nested
    @Optional
    DockerRegistryCredentials registryCredentials

    /**
     * Output file containing the image ID of the built image. Defaults to "$buildDir/.docker/$taskpath-imageId.txt"
     * with the leading colon of taskpath stripped and remaining colons replaced with underscores.
     *
     * @since 4.0.0
     */
    @OutputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    final RegularFileProperty imageIdFile = newOutputFile()

    @Internal
    final Property<String> imageId = project.objects.property(String)

    DockerBuildImage() {
        inputDir.set(project.file('docker'))

        String safeTaskPath = path.replaceFirst("^:", "").replaceAll(":", "_")
        File imageIdTextFile = new File(project.buildDir, ".docker/${safeTaskPath}-imageId.txt")
        imageIdFile.set(imageIdTextFile)

        if (imageIdTextFile.isFile()) {
            imageId.set(imageIdTextFile.text)
        }
    }

    @Override
    void runRemoteCommand(dockerClient) {
        logger.quiet "Building image using context '${inputDir.get().asFile}'."
        def buildImageCmd

        if (dockerFile.getOrNull()) {
            logger.quiet "Using Dockerfile '${dockerFile.get()}'"
            buildImageCmd = dockerClient.buildImageCmd()
                    .withBaseDirectory(inputDir.get().asFile)
                    .withDockerfile(dockerFile.get().asFile)
        } else {
            buildImageCmd = dockerClient.buildImageCmd(inputDir.get().asFile)
        }

        if(tag.getOrNull()) {
            logger.quiet "Using tag '${tag.get()}' for image."
            buildImageCmd.withTag(tag.get())
        } else if (tags.getOrNull()) {
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

        if (registryCredentials) {
            def authConfig = threadContextClassLoader.createAuthConfig(registryCredentials)
            def authConfigurations = threadContextClassLoader.createAuthConfigurations([authConfig])
            buildImageCmd.withBuildAuthConfigs(authConfigurations)
        }

        if (buildArgs.getOrNull()) {
            buildArgs.get().each { arg, value ->
                buildImageCmd = buildImageCmd.withBuildArg(arg, value)
            }
        }

        if (cacheFrom.getOrNull()) {
            // Workaround a bug in dockerjava that double-unmarshalls this argument
            def doubleMarshalledCacheFrom = ["[\"${cacheFrom.get().join('","')}\"]".toString()].toSet()
            buildImageCmd = buildImageCmd.withCacheFrom(doubleMarshalledCacheFrom)
        }

        def callback = nextHandler ? threadContextClassLoader.createBuildImageResultCallback(nextHandler)
                              : threadContextClassLoader.createBuildImageResultCallback()
        String createdImageId = buildImageCmd.exec(callback).awaitImageId()
        imageId.set(createdImageId)
        imageIdFile.get().asFile.text = createdImageId
        logger.quiet "Created image with ID '$createdImageId'."
    }
}
