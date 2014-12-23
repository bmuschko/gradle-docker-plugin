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
        task.instructions[0].build() == 'FROM ubuntu:12.04'
        task.instructions[1].build() == 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"'
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
        task.instructions[0].build() == 'FROM ubuntu:12.04'
        task.instructions[1].build() == 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"'
    }

    def "Can create Dockerfile by calling exposed methods"() {
        when:
        Dockerfile task = project.task('dockerfile', type: Dockerfile) {
            from 'ubuntu:14.04'
            maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
            runCommand 'echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
            runCommand 'apt-get update && apt-get clean'
            runCommand 'apt-get install -q -y openjdk-7-jre-headless && apt-get clean'
            addFile 'http://mirrors.jenkins-ci.org/war/1.563/jenkins.war', '/opt/jenkins.war'
            runCommand 'ln -sf /jenkins /root/.jenkins'
            entryPoint 'java', '-jar', '/opt/jenkins.war'
            exposePort 8080
            volume '/jenkins'
            defaultCommand ''
        }

        task.execute()

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM ubuntu:14.04
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
RUN apt-get update && apt-get clean
RUN apt-get install -q -y openjdk-7-jre-headless && apt-get clean
ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war
RUN ln -sf /jenkins /root/.jenkins
ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]
EXPOSE 8080
VOLUME ["/jenkins"]
CMD [""]
"""
        task.instructions.size() == 11
        task.instructions[0].build() == 'FROM ubuntu:14.04'
        task.instructions[1].build() == 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"'
        task.instructions[2].build() == 'RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
        task.instructions[3].build() == 'RUN apt-get update && apt-get clean'
        task.instructions[4].build() == 'RUN apt-get install -q -y openjdk-7-jre-headless && apt-get clean'
        task.instructions[5].build() == 'ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war'
        task.instructions[6].build() == 'RUN ln -sf /jenkins /root/.jenkins'
        task.instructions[7].build() == 'ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]'
        task.instructions[8].build() == 'EXPOSE 8080'
        task.instructions[9].build() == 'VOLUME ["/jenkins"]'
        task.instructions[10].build() == 'CMD [""]'
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
        task.instructions[0].build() == 'FROM ubuntu:12.04'
        task.instructions[1].build() == 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"'
    }
}
