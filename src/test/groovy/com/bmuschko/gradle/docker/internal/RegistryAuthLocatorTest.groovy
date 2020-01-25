package com.bmuschko.gradle.docker.internal

import com.github.dockerjava.api.model.AuthConfig
import org.gradle.api.logging.Logger
import spock.lang.Specification

class RegistryAuthLocatorTest extends Specification {

    private static final String CONFIG_LOCATION = '/auth-config/'
    private static final AuthConfig DEFAULT_AUTH_CONFIG = new AuthConfig()
    private Logger logger = Mock(Logger)

    def "AuthLocator works with empty config"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-empty.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('unauthenticated.registry.org/org/repo')

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        !config.getUsername()
        !config.getPassword()
        0 * logger.error(*_)
    }

    def "AuthLocator works using store"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-with-store.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        0 * logger.error(*_)
    }

    def "AuthLocator works using helper and empty auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-empty-auth-with-helper.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        0 * logger.error(*_)
    }

    def "AuthLocator works using auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-auth.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('container-registry.cloud.yandex.net/org/repo')

        then:
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        !config.getAuth()
        0 * logger.error(*_)
    }

    def "AuthLocator works using helper and existing auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-existing-auth-with-helper.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config.getRegistryAddress() == 'https://registry.example.com'
        config.getUsername() == "username"
        config.getPassword() == "secret"
        config.getEmail() == 'not@val.id'
        !config.getAuth()
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config for Docker Desktop config without existing credentials"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-docker-desktop.json', false)

        when:
        AuthConfig config = locator.lookupAuthConfig('https://index.docker.io/v1/org/repo')

        then:
        config == DEFAULT_AUTH_CONFIG
        2 * logger.error(*_)
    }

    def "AuthLocator works for Docker Desktop config without existing credentials"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-docker-desktop.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('https://index.docker.io/v1/org/repo')

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        config.getUsername() == 'mac_user'
        config.getPassword() == 'XXX'
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config when the file does not exist"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForMissingConfigFile('missing-file.json')
        locator.setLogger(logger)

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config == DEFAULT_AUTH_CONFIG
        1 * logger.debug('Looking up auth config for repository: registry.example.com')
        1 * logger.debug("RegistryAuthLocator has configFile: ${new File('missing-file.json').absolutePath} (does not exist) and commandPathPrefix: docker-credential-")
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config when the file is invalid"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('invalid.json')
        locator.setLogger(logger)

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config == DEFAULT_AUTH_CONFIG
        1 * logger.error(*_)
    }

    def "AuthLocator returns default config when the credentials tool is missing"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-missing-tool.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('registry.example.com/org/repo')

        then:
        config == DEFAULT_AUTH_CONFIG
        2 * logger.error(*_)
    }

    def "AuthLocator uses default config then store does not contain the credentials"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-auth-store.json')

        when:
        AuthConfig config = locator.lookupAuthConfig('https://index.docker.io/v1/org/repo')

        then:
        config == DEFAULT_AUTH_CONFIG
        0 * logger.error(*_)
        7 * logger.debug(*_)
    }

    private RegistryAuthLocator createAuthLocatorForExistingConfigFile(String configName, Boolean mockHelper = true){
        File configFile = new File(getClass().getResource(CONFIG_LOCATION + configName).toURI())
        RegistryAuthLocator locator
        if (mockHelper) {
            String command = configFile.getParentFile().getAbsolutePath() + '/docker-credential-'
            locator = new RegistryAuthLocator(configFile, command)
        } else {
            locator = new RegistryAuthLocator(configFile)
        }
        locator.setLogger(logger)
        locator
    }

    private RegistryAuthLocator createAuthLocatorForMissingConfigFile(String configName) {
        RegistryAuthLocator locator = new RegistryAuthLocator(new File(configName))
        locator.setLogger(logger)
        locator
    }
}
