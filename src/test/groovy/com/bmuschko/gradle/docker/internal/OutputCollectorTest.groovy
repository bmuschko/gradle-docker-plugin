package com.bmuschko.gradle.docker.internal

import com.bmuschko.gradle.docker.internal.OutputCollector
import spock.lang.Specification

class OutputCollectorTest extends Specification {

    def output = new StringBuilder()
    // We append a newline to the output to simulate the logs
    def collector = new OutputCollector({ s -> output.append(s).append('\n') })

    def "handles single line"() {
        when:
        collector.accept("A full line\n")
        then:
        output.toString() == "A full line\n"
    }

    def "handles multiple lines"() {
        when:
        collector.accept("One\nTwo\n")
        then:
        output.toString() == "One\nTwo\n"
    }

    def "handles split lines"() {
        when:
        collector.accept("One")
        collector.accept("Two")
        collector.accept("Three\n")
        then:
        output.toString() == "OneTwoThree\n"
    }

    def "does not write without newline"() {
        when:
        collector.accept("One")
        collector.accept("Two")
        then:
        output.toString().isEmpty()
    }

    def "writes when closed"() {
        when:
        collector.accept("One")
        collector.accept("Two")
        collector.close()
        then:
        output.toString() == "OneTwo\n"
    }

    def "does not duplicate strings"() {
        when:
        collector.accept("One\n")
        collector.accept("Two\n")
        then:
        output.toString() == "One\nTwo\n"
    }

    def "handles empty lines"() {
        when:
        collector.accept("")
        collector.accept("")
        collector.accept("")
        collector.accept("\n")
        then:
        output.toString() == "\n"
    }
}
