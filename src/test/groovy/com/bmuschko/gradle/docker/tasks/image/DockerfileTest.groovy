package com.bmuschko.gradle.docker.tasks.image

import spock.lang.Specification
import spock.lang.Unroll

import java.util.logging.Level
import java.util.logging.Logger

import static com.bmuschko.gradle.docker.tasks.image.Dockerfile.*
import static java.time.Duration.ofSeconds

class DockerfileTest extends Specification {
    private static final Logger LOG = Logger.getLogger(DockerfileTest.class.getCanonicalName())

    @Unroll
    def "#expectedKeyword instruction String representation is built correctly"() {
        given:
        LOG.fine "testing " + [
            instruction: instructionInstance.class,
            becomes: [
                keyword: expectedKeyword,
                withBuildText: expectedBuiltInstruction,
            ]
        ]

        when:
        def actual

        try {
            actual = instructionInstance.getText()
        } catch(Exception e) {
            LOG.info "Exception caught with message `${e.message}`"
            if(!expectedBuiltInstruction.equals(e.class)) {
                LOG.log(Level.WARNING, e.stackTrace?.join('\n\t'))
            }
            actual = e.class
        }

        then:
        instructionInstance.keyword == expectedKeyword
        actual == expectedBuiltInstruction

        where:
        instructionInstance                                                                           | expectedKeyword | expectedBuiltInstruction
        new GenericInstruction('FROM ubuntu:14.04')                                                   | 'FROM'          | 'FROM ubuntu:14.04'
        new FromInstruction(new From('ubuntu:14.04'))                                                 | 'FROM'          | 'FROM ubuntu:14.04'
        new FromInstruction(new From('ubuntu:14.04').withPlatform('linux/amd64'))                     | 'FROM'          | 'FROM --platform=linux/amd64 ubuntu:14.04'
        new FromInstruction(new From('ubuntu:14.04').withStage('build'))                              | 'FROM'          | 'FROM ubuntu:14.04 AS build'
        new FromInstruction(new From('ubuntu:14.04').withPlatform('linux/amd64').withStage('build'))  | 'FROM'          | 'FROM --platform=linux/amd64 ubuntu:14.04 AS build'
        new RunCommandInstruction('apt-get update && apt-get clean')                                  | 'RUN'           | 'RUN apt-get update && apt-get clean'
        new DefaultCommandInstruction('ping google.com')                                              | 'CMD'           | 'CMD ["ping google.com"]'
        new ExposePortInstruction(8080)                                                               | 'EXPOSE'        | 'EXPOSE 8080'
        new ExposePortInstruction(8080, 9090)                                                         | 'EXPOSE'        | 'EXPOSE 8080 9090'
        new EnvironmentVariableInstruction('OS', 'Linux')                                             | 'ENV'           | 'ENV OS=Linux'
        new EnvironmentVariableInstruction('', 'Linux')                                               | 'ENV'           | IllegalArgumentException.class
        new EnvironmentVariableInstruction(' ', 'Linux')                                              | 'ENV'           | IllegalArgumentException.class
        new EnvironmentVariableInstruction('OS', '"Linux"')                                           | 'ENV'           | 'ENV OS="Linux"'
        new EnvironmentVariableInstruction('OS', 'Linux or Windows')                                  | 'ENV'           | 'ENV OS="Linux or Windows"'
        new EnvironmentVariableInstruction('long', '''Multiple line env 
with linebreaks in between''')                                                                        | 'ENV'           | "ENV long=\"Multiple line env \\\n\
with linebreaks in between\""
        new EnvironmentVariableInstruction(['OS': 'Linux'])                                           | 'ENV'           | 'ENV OS=Linux'
        new EnvironmentVariableInstruction(['long': '''Multiple line env 
with linebreaks in between'''])                                                                       | 'ENV'           | "ENV long=\"Multiple line env \\\n\
with linebreaks in between\""
        new EnvironmentVariableInstruction(['OS': 'Linux', 'TZ': 'UTC'])                              | 'ENV'           | 'ENV OS=Linux TZ=UTC'
        new AddFileInstruction(new File('config.xml', '/test'))                                       | 'ADD'           | 'ADD config.xml /test'
        new CopyFileInstruction(new CopyFile('config.xml', '/test'))                                  | 'COPY'          | 'COPY config.xml /test'
        new EntryPointInstruction('java', '-jar', '/opt/jenkins.war')                                 | 'ENTRYPOINT'    | 'ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]'
        new VolumeInstruction('/jenkins')                                                             | 'VOLUME'        | 'VOLUME ["/jenkins"]'
        new UserInstruction('ENV JAVA_HOME /usr/java')                                                | 'USER'          | 'USER ENV JAVA_HOME /usr/java'
        new WorkDirInstruction('/some/dir')                                                           | 'WORKDIR'       | 'WORKDIR /some/dir'
        new OnBuildInstruction('ENV JAVA_HOME /usr/java')                                             | 'ONBUILD'       | 'ONBUILD ENV JAVA_HOME /usr/java'
        new LabelInstruction(['group': 'artificial' ])                                                | 'LABEL'         | 'LABEL group=artificial'
        new LabelInstruction(['description': 'Single label' ])                                        | 'LABEL'         | 'LABEL description="Single label"'
        new LabelInstruction(['"un subscribe"': 'true' ])                                             | 'LABEL'         | 'LABEL "un subscribe"=true'
        new LabelInstruction(['description': 'Multiple labels', 'version': '1.0' ])                   | 'LABEL'         | 'LABEL description="Multiple labels" version=1.0'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running"))                             | 'HEALTHCHECK'   | 'HEALTHCHECK CMD /bin/check-running'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running").withInterval(ofSeconds(10))) | 'HEALTHCHECK'   | 'HEALTHCHECK --interval=10s CMD /bin/check-running'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running").withTimeout(ofSeconds(20)))  | 'HEALTHCHECK'   | 'HEALTHCHECK --timeout=20s CMD /bin/check-running'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running")
            .withStartInterval(ofSeconds(30)))                                                        | 'HEALTHCHECK'   | 'HEALTHCHECK --start-interval=30s CMD /bin/check-running'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running")
            .withStartPeriod(ofSeconds(40)))                                                          | 'HEALTHCHECK'   | 'HEALTHCHECK --start-period=40s CMD /bin/check-running'
        new HealthcheckInstruction(new Healthcheck("/bin/check-running").withRetries(5))              | 'HEALTHCHECK'   | 'HEALTHCHECK --retries=5 CMD /bin/check-running'
    }
}
