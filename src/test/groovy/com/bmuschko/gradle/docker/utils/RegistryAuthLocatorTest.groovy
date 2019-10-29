package com.bmuschko.gradle.docker.utils

import com.github.dockerjava.api.model.AuthConfig
import spock.lang.Specification


class RegistryAuthLocatorTest extends Specification {

    private static final CONFIG_LOCATION = "/auth-config/"

    def "AuthLocator works with empty config"() {
        when:
        def locator = createTestAuthLocator("config-empty.json")
        def config = locator.lookupAuthConfig("unauthenticated.registry.org/org/repo");
        then:
        config.getRegistryAddress() == "https://index.docker.io/v1/"
        config.getUsername() == null
        config.getPassword() == null
    }

    def "AuthLocator works using store"() {
        when:
        def locator = createTestAuthLocator("config-with-store.json")
        def config = locator.lookupAuthConfig("registry.example.com/org/repo");
        then:
        config.getRegistryAddress() == "url"
        config.getUsername() == "username"
        config.getPassword() == "secret"
    }

    def "AuthLocator works using helper and empty auth"() {
        when:
        def locator = createTestAuthLocator("config-empty-auth-with-helper.json")
        def config = locator.lookupAuthConfig("registry.example.com/org/repo");
        then:
        config.getRegistryAddress() == "url"
        config.getUsername() == "username"
        config.getPassword() == "secret"
    }

    def "AuthLocator works using auth"() {
        when:
        def locator = createTestAuthLocator("config-auth.json")
        def config = locator.lookupAuthConfig("container-registry.cloud.yandex.net/org/repo");
        then:
        config.getUsername() == null
        config.getAuth() == "authkey"
    }

    def "AuthLocator works using helper and existing auth"() {
        when:
        def locator = createTestAuthLocator("config-existing-auth-with-helper.json")
        def config = locator.lookupAuthConfig("registry.example.com/org/repo");
        then:
        config.getRegistryAddress() == "https://registry.example.com"
        config.getUsername() == null
        config.getEmail() == "not@val.id"
        config.getAuth() == "encoded auth token"
    }

    def "AuthLocator returns default config when the file does not exist"() {
        when:
        def config = new AuthConfig()
        def locator = new RegistryAuthLocator(new File("missing-file.json"))
        then:
        locator.lookupAuthConfig("registry.example.com/org/repo") == config
    }

    def "AuthLocator returns default config when the file is invalid"() {
        when:
        def config = new AuthConfig()
        def locator = new RegistryAuthLocator(new File("invalid.json"))
        then:
        locator.lookupAuthConfig("registry.example.com/org/repo") == config
    }

    def "AuthLocator returns default config when the credentials tool is missing"() {
        when:
        def config = new AuthConfig()
        def locator = createTestAuthLocator("config-missing-tool.json")
        then:
        locator.lookupAuthConfig("registry.example.com/org/repo") == config
    }

    def "AuthLocator works with localhost"() {
        when:
        def locator = new RegistryAuthLocator()
        def config =  locator.lookupAuthConfig("localhost:5001/abc")
        then:
        config.username == "testuser"
        config.password == "testpassword"
    }

    private RegistryAuthLocator createTestAuthLocator(String configName){
        def configFile = new File(getClass().getResource(CONFIG_LOCATION + configName).toURI())
        def command = configFile.getParentFile().getAbsolutePath() + "/docker-credential-"
        return new RegistryAuthLocator(configFile, command)
    }

}
