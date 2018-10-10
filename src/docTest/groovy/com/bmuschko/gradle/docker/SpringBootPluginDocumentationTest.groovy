package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class SpringBootPluginDocumentationTest extends AbstractDocumentationTest {

    @Unroll
    def "can execute basic Spring Boot project [#dsl.language]"() {
        given:
        copySampleCode("spring-boot-plugin/basic/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('dockerPushImage')

        where:
        dsl << ALL_DSLS
    }

    @Unroll
    def "can execute Spring Boot project on Tomcat [#dsl.language]"() {
        given:
        copySampleCode("spring-boot-plugin/tomcat/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('dockerPushImage')

        where:
        dsl << ALL_DSLS
    }
}
