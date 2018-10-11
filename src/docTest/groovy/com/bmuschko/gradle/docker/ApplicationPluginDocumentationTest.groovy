package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class ApplicationPluginDocumentationTest extends AbstractDocumentationTest {

    @Unroll
    def "can execute basic application project [#dsl.language]"() {
        given:
        copySampleCode("application-plugin/basic/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('dockerPushImage')

        where:
        dsl << ALL_DSLS
    }

    @Unroll
    def "can execute application project on Jetty [#dsl.language]"() {
        given:
        copySampleCode("application-plugin/jetty/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('dockerPushImage')

        where:
        dsl << ALL_DSLS
    }
}
