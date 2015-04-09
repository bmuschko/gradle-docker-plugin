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

import com.bmuschko.gradle.docker.tasks.DockerTaskIntegrationTest
import org.apache.commons.io.FileUtils
import org.gradle.api.Task

class DockerBuildImageSeparatedIntegrationTest extends DockerTaskIntegrationTest {
    File dockerInputDir
    File dockerDockerFile

    def setup() {
        dockerInputDir = createDir(new File(projectDir, 'docker'))
        dockerDockerFile = createDockerfile()
    }

    @Override
    Task createAndConfigureTask() {
        project.task('buildImage', type: DockerBuildImage) {
            inputDir = dockerInputDir
            dockerFile = dockerDockerFile
            tag = 'bmuschko/myImage'
        }
    }

    private File createDockerfile() {
        File dockerFile = new File(projectDir, 'Dockerfile')

        FileUtils.writeStringToFile(dockerFile, """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
""")
        dockerFile
    }
}
