package com.bmuschko.gradle.docker

import spock.lang.Specification
import spock.lang.Unroll

class TestPreconditionIntegrationTest extends Specification {
    def "Pinging URL with unsupported protocol throws exception"() {
        when:
        TestPrecondition.isUrlReachable(new URL('file:///Users/foo/bar'))

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Unsupported URL protocol 'file'"
    }

    @Unroll
    def "Can ping URL #url"() {
        when:
        boolean success = TestPrecondition.isUrlReachable(new URL(url))

        then:
        success

        where:
        url << ['http://www.google.com', 'https://www.google.com']
    }
}
