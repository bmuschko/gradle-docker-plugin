package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult

import java.nio.file.Files
import java.nio.file.Paths

class DockerfileFunctionalTest extends AbstractFunctionalTest {
    static final String DOCKERFILE_TASK_NAME = 'dockerfile'

    private void setupDockerTemplateFile() {
        File source = new File(TestConfiguration.class.getClassLoader().getResource("Dockerfile.template").toURI())
        if (source.exists()) {
            File resourcesDir = new File(projectDir, 'src/main/docker/')
            if (resourcesDir.mkdirs()) {
                if (Files.copy(source.toPath(),Paths.get(projectDir.path, 'src/main/docker/Dockerfile.template')).toFile().length() != source.length()) {
                    throw new GradleException("File could not be successfully copied")
                }
            } else {
                throw new IOException("can not create the directory ${resourcesDir.absolutePath}")
            }
        }
    }

    def "Executing a Dockerfile task without specified instructions throws exception"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile)
"""
        when:
        BuildResult buildResult = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        buildResult.output.contains('Please specify instructions for your Dockerfile')
        !new File(projectDir, 'build/docker/Dockerfile').exists()
    }

    def "Specifying FROM instruction as first statement is mandatory"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}
"""
        when:
        BuildResult buildResult = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        buildResult.output.contains('The first instruction of a Dockerfile has to be FROM')
        !new File(projectDir, 'build/docker/Dockerfile').exists()
    }

    def "Can create minimal Dockerfile in default location"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    from 'alpine:3.4'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM alpine:3.4
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile using all instruction methods"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    from 'alpine:3.4'
    maintainer 'Benjamin Muschko "benjamin.muschko@gmail.com"'
    runCommand 'echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list'
    defaultCommand 'echo', 'some', 'command'
    exposePort 8080, 14500
    environmentVariable 'ENV_VAR_KEY', 'envVarVal'
    environmentVariable ENV_VAR_A: 'val_a'
    environmentVariable ENV_VAR_B: 'val_b', ENV_VAR_C: 'val_c'
    addFile 'http://mirrors.jenkins-ci.org/war/1.563/jenkins.war', '/opt/jenkins.war'
    copyFile 'http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar', '/opt/h2.jar'
    entryPoint 'java', '-jar', '/opt/jenkins.war'
    volume '/jenkins', '/myApp'
    user 'root'
    workingDir '/tmp'
    onBuild 'RUN echo "Hello World"'
    label version: '1.0'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM alpine:3.4
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
CMD ["echo", "some", "command"]
EXPOSE 8080 14500
ENV ENV_VAR_KEY envVarVal
ENV "ENV_VAR_A"="val_a"
ENV "ENV_VAR_B"="val_b" "ENV_VAR_C"="val_c"
ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war
COPY http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar /opt/h2.jar
ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]
VOLUME ["/jenkins", "/myApp"]
USER root
WORKDIR /tmp
ONBUILD RUN echo "Hello World"
LABEL "version"="1.0"
"""
    }

    def "Can create Dockerfile by adding instances of Instruction"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instructions = [new Dockerfile.FromInstruction('alpine:3.4'),
                    new Dockerfile.MaintainerInstruction('Benjamin Muschko "benjamin.muschko@gmail.com"')]
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM alpine:3.4
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile by adding raw instructions"() {
        given:
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instruction 'FROM alpine:3.4'
    instruction { 'MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"' }
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM alpine:3.4
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
"""
    }

    def "Can create Dockerfile from template file"() {
        given:
        setupDockerTemplateFile()
        buildFile << """
import com.bmuschko.gradle.docker.tasks.image.Dockerfile

task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
    instructionsFromTemplate 'src/main/docker/Dockerfile.template'
}
"""
        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
            """FROM alpine:3.4
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com
"""
    }
}
