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
package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.bundling.Tar

/**
 *
 */
class DockerApplicationPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(DockerRemoteApiPlugin)

        project.plugins.withType(ApplicationPlugin) {
            Tar tarTask = project.tasks.getByName(ApplicationPlugin.TASK_DIST_TAR_NAME)
            String archiveNameWithoutFileExtension = tarTask.archiveName - ".${tarTask.extension}"

            project.task('distDocker', type: Dockerfile) {
                addFile tarTask.outputs.files.singleFile
                entryPoint "/$archiveNameWithoutFileExtension"
            }
        }
    }
}
