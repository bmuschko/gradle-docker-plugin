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
package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.plugins.docker.tasks.AbstractDockerTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

class DockerCommitImage extends AbstractDockerTask {
    /**
     * Name of the source container.
     */
    @Input
    String container

    /**
     * Repository.
     */
    @Input
    @Optional
    String repository

    /**
     * Commit tag.
     */
    @Input
    @Optional
    String tag

    /**
     * Commit message.
     */
    @Input
    @Optional
    String message

    /**
     * Author of image e.g. Benjamin Muschko.
     */
    @Input
    @Optional
    String author

    @Input
    @Optional
    String run

    @Override
    void runRemoteCommand(URLClassLoader classLoader) {
        logger.quiet "Commiting image for container '${getContainer()}'."
        def dockerClient = getDockerClient(classLoader)
        def commitConfig = createCommitConfig(classLoader)
        String commitId = dockerClient.commit(commitConfig)
        logger.quiet "Created image with ID '$commitId'."
    }

    private createCommitConfig(URLClassLoader classLoader) {
        Class commitConfigClass = classLoader.loadClass('com.kpelykh.docker.client.model.CommitConfig')
        def commitConfig = commitConfigClass.newInstance()
        commitConfig.container = getContainer()
        commitConfig.repo = getRepository()
        commitConfig.tag = getTag()
        commitConfig.message = getMessage()
        commitConfig.author = getAuthor()
        commitConfig.run = getRun()
        logger.info "Commit configuration: $commitConfig"
        commitConfig
    }
}