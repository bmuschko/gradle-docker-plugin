package com.bmuschko.gradle.docker.utils

import com.github.dockerjava.api.model.AuthConfig
import spock.lang.Specification

class RegistryAuthLocatorTest extends Specification {

    private static final String CONFIG_LOCATION = '/auth-config/'
    private static final AuthConfig DEFAULT_AUTH_CONFIG = new AuthConfig()

    def "AuthLocator works with empty config"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-empty.json')
        AuthConfig config = locator.lookupAuthConfig('unauthenticated.registry.org/org/repo')

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        !config.getUsername()
        !config.getPassword()
    }

    def "AuthLocator works using store"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-with-store.json')
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
    }

    def "AuthLocator works using helper and empty auth"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-empty-auth-with-helper.json')
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
    }

    def "AuthLocator works using auth"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-auth.json')
        AuthConfig config = locator.lookupAuthConfig('container-registry.cloud.yandex.net/org/repo')

        then:
        config.getUsername() == null
        config.getAuth() == 'authkey'
    }

    def "AuthLocator works using helper and existing auth"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-existing-auth-with-helper.json')
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'https://registry.example.com'
        !config.getUsername()
        config.getEmail() == 'not@val.id'
        config.getAuth() == 'encoded auth token'
    }

    def "AuthLocator returns default config when the file does not exist"() {
        when:
        RegistryAuthLocator locator = new RegistryAuthLocator(new File('missing-file.json'))

        then:
        locator.lookupAuthConfig('registry.example.com/org/repo') == DEFAULT_AUTH_CONFIG
    }

    def "AuthLocator returns default config when the file is invalid"() {
        when:
        RegistryAuthLocator locator = new RegistryAuthLocator(new File('invalid.json'))

        then:
        locator.lookupAuthConfig('registry.example.com/org/repo') == DEFAULT_AUTH_CONFIG
    }

    def "AuthLocator returns default config when the credentials tool is missing"() {
        when:
        RegistryAuthLocator locator = createTestAuthLocator('config-missing-tool.json')

        then:
        locator.lookupAuthConfig('registry.example.com/org/repo') == DEFAULT_AUTH_CONFIG
    }

    private RegistryAuthLocator createTestAuthLocator(String configName){
        File configFile = new File(getClass().getResource(CONFIG_LOCATION + configName).toURI())
        String command = configFile.getParentFile().getAbsolutePath() + '/docker-credential-'
        new RegistryAuthLocator(configFile, command)
    }
}
