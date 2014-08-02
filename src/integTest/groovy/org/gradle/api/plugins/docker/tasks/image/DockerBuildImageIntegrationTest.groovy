package org.gradle.api.plugins.docker.tasks.image

import org.gradle.api.Task
import org.gradle.api.plugins.docker.tasks.DockerTaskIntegrationTest
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.mvn3.org.codehaus.plexus.util.FileUtils

class DockerBuildImageIntegrationTest extends DockerTaskIntegrationTest {
    def "Throws ConnectionException for unreachable Docker server"() {
        given:
        File dockerInputDir = new File(projectDir, 'docker')
        dockerInputDir.mkdirs()
        File dockerFile = new File(dockerInputDir, 'Dockerfile')

        FileUtils.fileWrite(dockerFile, """
FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
""")

        when:
        Task task = project.task('buildImage', type: DockerBuildImage) {
            inputDir = dockerInputDir
            tag = 'bmuschko/myImage'
        }
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        t.cause.message.contains("Connection to $DockerTaskIntegrationTest.SERVER_URL refused")
    }
}
