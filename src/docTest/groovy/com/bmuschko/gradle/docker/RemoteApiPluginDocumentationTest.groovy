package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore
import spock.lang.Unroll

class RemoteApiPluginDocumentationTest  extends AbstractDocumentationTest {

    @Unroll
    def "can apply plugin with plugins DSL [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/apply-plugin-dsl/$dsl.language")

        expect:
        build('tasks', '--all')

        where:
        dsl << ALL_DSLS
    }

    @Unroll
    def "can create task and configure extension [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/basic/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('buildMyAppImage')

        where:
        dsl << ALL_DSLS
    }

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
    def "can react to events [#dsl.language]"() {
        given:
        copySampleCode("remote-api-plugin/reactive-streams/$dsl.language")

        when:
        BuildResult result = build('tasks', '--all')

        then:
        result.output.contains('removeContainer1')
        result.output.contains('removeContainer2')
        result.output.contains('logContainer')

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
