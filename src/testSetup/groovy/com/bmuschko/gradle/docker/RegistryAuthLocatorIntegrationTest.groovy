package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.internal.RegistryAuthLocator
import com.github.dockerjava.api.model.AuthConfig
import spock.lang.Requires

class RegistryAuthLocatorIntegrationTest {

    @Requires({ TestPrecondition.DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE })
    def "AuthLocator works with localhost"() {
        when:
        RegistryAuthLocator locator = new RegistryAuthLocator()
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig("localhost:5001/abc")

        then:
        config.username == "testuser"
        config.password == "testpassword"
    }
}
