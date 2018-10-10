package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore
import spock.lang.Unroll

class RemoteApiPluginDocumentationTest  extends AbstractDocumentationTest {

    @Ignore
    @Unroll
    def "can build image [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/build-image/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('buildImage')

        where:
        dsl << ALL_DSLS
    }

    @Unroll
    def "can run functional tests using a container fixture [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/functional-test/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('functionalTestMyApp')

        where:
        dsl << ALL_DSLS
    }

    @Unroll
    def "can link containers [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/linking-containers/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('functionalTestMyApp')

        where:
        dsl << ALL_DSLS
    }
}
