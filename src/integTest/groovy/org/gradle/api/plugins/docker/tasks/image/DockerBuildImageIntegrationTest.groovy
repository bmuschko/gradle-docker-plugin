package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest
import org.gradle.mvn3.org.codehaus.plexus.util.FileUtils

class DockerBuildImageIntegrationTest extends DockerTaskIntegrationTest {
    @Override
    Task createAndConfigureTask() {
        project.task('buildImage', type: DockerBuildImage) {
            inputDir = createDockerfile()
            tag = 'bmuschko/myImage'
        }
    }

    private File createDockerfile() {
        File dockerInputDir = new File(projectDir, 'docker')
        dockerInputDir.mkdirs()
        File dockerFile = new File(dockerInputDir, 'Dockerfile')

        FileUtils.fileWrite(dockerFile, """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
""")
        dockerFile
    }
}
