package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerfileFunctionalTest extends AbstractGroovyDslFunctionalTest {
    private static final String DOCKERFILE_TASK_NAME = 'dockerfile'
    private static final String DOCKERFILE_TASK_PATH = ":$DOCKERFILE_TASK_NAME".toString()

    def setup() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
        """
    }

    def "Executing a Dockerfile task without specified instructions throws exception"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile)
        """

        when:
        BuildResult buildResult = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        !defaultDockerfile().exists()
        buildResult.output.contains('Please specify instructions for your Dockerfile')
    }

    def "Instruction validation is performed during execution phase"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                // expected missing from
                defaultCommand 'foo'
            }

            task any
        """

        expect:
        build('any')
    }

    def "First instruction has to be FROM or ARG"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                label(['maintainer': 'benjamin.muschko@gmail.com'])
            }
        """

        when:
        BuildResult result = buildAndFail(DOCKERFILE_TASK_NAME)

        then:
        !defaultDockerfile().exists()
        result.output.contains('The first instruction of a Dockerfile has to be FROM (or ARG for Docker later than 17.05)')
    }

    def "Can specify FROM instruction as first statement"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
""")
    }

    def "Can specify ARG instruction as first statement"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                arg 'baseImage=$TEST_IMAGE_WITH_TAG'
                from '\$baseImage'
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""ARG baseImage=$TEST_IMAGE_WITH_TAG
FROM \$baseImage
""")
    }

    def "Can create minimal Dockerfile in default location"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'benjamin.muschko@gmail.com'])
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
""")
    }

    def "Can create Dockerfile using all instruction methods"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'benjamin.muschko@gmail.com'])
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
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
CMD ["echo", "some", "command"]
EXPOSE 8080 14500
ENV ENV_VAR_KEY envVarVal
ENV ENV_VAR_A=val_a
ENV ENV_VAR_B=val_b ENV_VAR_C=val_c
ADD http://mirrors.jenkins-ci.org/war/1.563/jenkins.war /opt/jenkins.war
COPY http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar /opt/h2.jar
ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]
VOLUME ["/jenkins", "/myApp"]
USER root
WORKDIR /tmp
ONBUILD RUN echo "Hello World"
LABEL version=1.0
""")
    }

    def "Can create Dockerfile by adding instances of Instruction"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                instructions.add(new Dockerfile.FromInstruction('$TEST_IMAGE_WITH_TAG'))
                instructions.add(new Dockerfile.LabelInstruction(['maintainer': 'benjamin.muschko@gmail.com']))
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
""")
    }

    def "Can create Dockerfile by adding raw instructions"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                instruction('FROM $TEST_IMAGE_WITH_TAG')
                instruction('LABEL maintainer=benjamin.muschko@gmail.com')
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
""")
    }

    def "Can create Dockerfile from template file"() {
        given:
        File dockerDir = temporaryFolder.newFolder('src', 'main', 'docker')
        new File(dockerDir, 'Dockerfile.template') << """FROM alpine:3.4
LABEL maintainer=benjamin.muschko@gmail.com"""
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                instructionsFromTemplate(file('src/main/docker/Dockerfile.template'))
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG
LABEL maintainer=benjamin.muschko@gmail.com
""")
    }

    def "Dockerfile task can be up-to-date"() {
        given:
        buildFile << """
            ext.labelVersion = project.properties.getOrDefault('labelVersion', '1.0')
            
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                instruction('FROM $TEST_IMAGE_WITH_TAG')
                instruction('LABEL maintainer=benjamin.muschko@gmail.com')
                label([ver: labelVersion])
            }
        """

        when:
        BuildResult result = build(DOCKERFILE_TASK_NAME)

        then:
        result.task(DOCKERFILE_TASK_PATH).outcome == TaskOutcome.SUCCESS

        when:
        result = build(DOCKERFILE_TASK_NAME)

        then:
        result.task(DOCKERFILE_TASK_PATH).outcome == TaskOutcome.UP_TO_DATE

        when:
        result = build(DOCKERFILE_TASK_NAME, "-PlabelVersion=1.1")

        then:
        result.task(DOCKERFILE_TASK_PATH).outcome == TaskOutcome.SUCCESS
    }

    def "can create multi-stage builds"() {
        given:
        buildFile << """
            task ${DOCKERFILE_TASK_NAME}(type: Dockerfile) {
                from('$TEST_IMAGE_WITH_TAG', 'builder')
                label(['maintainer': 'benjamin.muschko@gmail.com'])
                copyFile('http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar', '/opt/h2.jar')
                from('$TEST_IMAGE_WITH_TAG', 'prod')
                copyFile('/opt/h2.jar', '/opt/h2.jar', 'builder')
            }
        """

        when:
        build(DOCKERFILE_TASK_NAME)

        then:
        assertDockerfileContent("""FROM $TEST_IMAGE_WITH_TAG AS builder
LABEL maintainer=benjamin.muschko@gmail.com
COPY http://hsql.sourceforge.net/m2-repo/com/h2database/h2/1.4.184/h2-1.4.184.jar /opt/h2.jar
FROM alpine:3.4 AS prod
COPY --from=builder /opt/h2.jar /opt/h2.jar
""")
    }

    private void assertDockerfileContent(String expectedContent) {
        File dockerfile = defaultDockerfile()
        assert dockerfile.isFile()
        assert dockerfile.text == expectedContent
    }

    private File defaultDockerfile() {
        new File(projectDir, 'build/docker/Dockerfile')
    }
}
