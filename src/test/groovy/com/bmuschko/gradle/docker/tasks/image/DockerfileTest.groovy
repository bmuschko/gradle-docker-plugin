package com.bmuschko.gradle.docker.tasks.image

import spock.lang.Specification

import static com.bmuschko.gradle.docker.tasks.image.Dockerfile.*

class DockerfileTest extends Specification {
    def "Instruction String representation is built correctly"() {
        expect:
        instructionInstance.keyword == keyword
        instructionInstance.build() == builtInstruction

        where:
        instructionInstance                                                             | keyword      | builtInstruction
        new GenericInstruction('FROM ubuntu:14.04')                                     | 'FROM'       | 'FROM ubuntu:14.04'
        new GenericInstruction({ 'FROM ubuntu:14.04' })                                 | 'FROM'       | 'FROM ubuntu:14.04'
        new FromInstruction('ubuntu:14.04')                                             | 'FROM'       | 'FROM ubuntu:14.04'
        new FromInstruction({ 'ubuntu:14.04' })                                         | 'FROM'       | 'FROM ubuntu:14.04'
        new MaintainerInstruction('John Doe "john.doe@gmail.com"')                      | 'MAINTAINER' | 'MAINTAINER John Doe "john.doe@gmail.com"'
        new MaintainerInstruction({ 'John Doe "john.doe@gmail.com"' })                  | 'MAINTAINER' | 'MAINTAINER John Doe "john.doe@gmail.com"'
        new RunCommandInstruction('apt-get update && apt-get clean')                    | 'RUN'        | 'RUN apt-get update && apt-get clean'
        new RunCommandInstruction({ 'apt-get update && apt-get clean' })                | 'RUN'        | 'RUN apt-get update && apt-get clean'
        new DefaultCommandInstruction('ping google.com')                                | 'CMD'        | 'CMD ["ping google.com"]'
        new DefaultCommandInstruction({ 'ping google.com' })                            | 'CMD'        | 'CMD ["ping google.com"]'
        new ExposePortInstruction(8080)                                                 | 'EXPOSE'     | 'EXPOSE 8080'
        new ExposePortInstruction({ 8080 })                                             | 'EXPOSE'     | 'EXPOSE 8080'
        new ExposePortInstruction(8080, 9090)                                           | 'EXPOSE'     | 'EXPOSE 8080 9090'
        new ExposePortInstruction({ [8080, 9090] })                                     | 'EXPOSE'     | 'EXPOSE 8080 9090'
        new EnvironmentVariableInstruction('OS', 'Linux')                               | 'ENV'        | 'ENV OS Linux'
        new EnvironmentVariableInstruction(['OS': 'Linux'])                             | 'ENV'        | 'ENV "OS"="Linux"'
        new EnvironmentVariableInstruction({ ['OS': 'Linux'] })                         | 'ENV'        | 'ENV "OS"="Linux"'
        new EnvironmentVariableInstruction(['OS': 'Linux', 'TZ': 'UTC'])                | 'ENV'        | 'ENV "OS"="Linux" "TZ"="UTC"'
        new EnvironmentVariableInstruction({ ['OS': 'Linux', 'TZ': 'UTC'] })            | 'ENV'        | 'ENV "OS"="Linux" "TZ"="UTC"'
        new AddFileInstruction('config.xml', '/test')                                   | 'ADD'        | 'ADD config.xml /test'
        new AddFileInstruction({ 'config.xml' }, { '/test' })                           | 'ADD'        | 'ADD config.xml /test'
        new CopyFileInstruction('config.xml', '/test')                                  | 'COPY'       | 'COPY config.xml /test'
        new CopyFileInstruction({ 'config.xml' }, { '/test' })                          | 'COPY'       | 'COPY config.xml /test'
        new EntryPointInstruction('java', '-jar', '/opt/jenkins.war')                   | 'ENTRYPOINT' | 'ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]'
        new EntryPointInstruction({ ['java', '-jar', '/opt/jenkins.war'] })             | 'ENTRYPOINT' | 'ENTRYPOINT ["java", "-jar", "/opt/jenkins.war"]'
        new VolumeInstruction('/jenkins')                                               | 'VOLUME'     | 'VOLUME ["/jenkins"]'
        new VolumeInstruction({ '/jenkins' })                                           | 'VOLUME'     | 'VOLUME ["/jenkins"]'
        new UserInstruction('ENV JAVA_HOME /usr/java')                                  | 'USER'       | 'USER ENV JAVA_HOME /usr/java'
        new UserInstruction({ 'ENV JAVA_HOME /usr/java' })                              | 'USER'       | 'USER ENV JAVA_HOME /usr/java'
        new WorkDirInstruction('/some/dir')                                             | 'WORKDIR'    | 'WORKDIR /some/dir'
        new WorkDirInstruction({ '/some/dir' })                                         | 'WORKDIR'    | 'WORKDIR /some/dir'
        new OnBuildInstruction('ENV JAVA_HOME /usr/java')                               | 'ONBUILD'    | 'ONBUILD ENV JAVA_HOME /usr/java'
        new OnBuildInstruction({ 'ENV JAVA_HOME /usr/java' })                           | 'ONBUILD'    | 'ONBUILD ENV JAVA_HOME /usr/java'
        new LabelInstruction(['description': 'Single label' ])                          | 'LABEL'      | 'LABEL "description"="Single label"'
        new LabelInstruction({ ['description': 'Single label' ] })                      | 'LABEL'      | 'LABEL "description"="Single label"'
        new LabelInstruction(['description': 'Multiple labels', 'version': '1.0' ])     | 'LABEL'      | 'LABEL "description"="Multiple labels" "version"="1.0"'
        new LabelInstruction({ ['description': 'Multiple labels', 'version': '1.0' ] }) | 'LABEL'      | 'LABEL "description"="Multiple labels" "version"="1.0"'
    }
}
