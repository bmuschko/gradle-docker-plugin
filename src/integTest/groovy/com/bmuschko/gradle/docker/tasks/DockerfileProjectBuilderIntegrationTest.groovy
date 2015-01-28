package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.ProjectBuilderIntegrationTest
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.apache.commons.lang.exception.ExceptionUtils
import org.gradle.api.tasks.TaskExecutionException

class DockerfileProjectBuilderIntegrationTest extends ProjectBuilderIntegrationTest {
    def "Executing a Dockerfile task without specified instructions throws exception"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile)
        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        ExceptionUtils.getRootCause(t).message == 'Please specify instructions for your Dockerfile'
        !new File(projectDir, 'build/docker/Dockerfile').exists()
    }

    def "Executing a Dockerfile task that does not specify FROM instruction as first statement throws exception"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
        }

        task.execute()

        then:
        Throwable t = thrown(TaskExecutionException)
        ExceptionUtils.getRootCause(t).message == 'The first instruction of a Dockerfile has to be FROM'
        !new File(projectDir, 'build/docker/Dockerfile').exists()
    }

    def "Can create minimal Dockerfile in default location"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            from 'ubuntu:12.04'
            maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        task.instructions.size() == 2
    }

    def "Can create minimal Dockerfile in custom location"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            destFile = project.file('build/sample/MyDockerfile')
            from 'ubuntu:12.04'
            maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/sample/MyDockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        task.instructions.size() == 2
    }

    def "Can create Dockerfile by calling exposed methods"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            from 'ubuntu:14.04'
            maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
            runCommand 'echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
            defaultCommand 'echo', 'some', 'command'
            exposePort 8080, 14500
            environmentVariable 'ENV_VAR_KEY', 'envVarVal'
            addFile 'http://mirrors.jenkins-ci.org/war/1.563/jenkins.war', '/opt/jenkins.war'
            copyFile 'http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar', '/opt/h2.jar'
            entryPoint 'java', '-jar', '/opt/jenkins.war'
            volume '/jenkins', '/myApp'
            user 'root'
            workingDir '/tmp'
            onBuild 'RUN echo "Hello World"'
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM ubuntu:14.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
CMD ["echo", "some", "command"]
EXPOSE 8080 14500
ENV ENV_VAR_KEY envVarVal
ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war
COPY http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar /opt/h2.jar
ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]
VOLUME ["/jenkins", "/myApp"]
USER root
WORKDIR /tmp
ONBUILD RUN echo "Hello World"
"""

        task.instructions.size() == 13
    }

    def "Can create Dockerfile by adding instances of Instruction"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            instructions = [new Dockerfile.FromInstruction('ubuntu:12.04'),
                            new Dockerfile.MaintainerInstruction('Benjamin Muschko "benjamin.muschko@gmail.com"')]
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        task.instructions.size() == 2
    }

    def "Can create Dockerfile by adding plain instruction"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            instruction 'FROM ubuntu:12.04'
            instruction { 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"' }
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM ubuntu:12.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
        task.instructions.size() == 2
    }
}
