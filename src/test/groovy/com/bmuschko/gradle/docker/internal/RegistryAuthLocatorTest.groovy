package com.bmuschko.gradle.docker.internal

import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.AuthConfigurations
import org.gradle.api.logging.Logger
import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ os.windows })
class RegistryAuthLocatorTest extends Specification {

    private static final String CONFIG_LOCATION = '/auth-config/'
    private static final AuthConfig DEFAULT_AUTH_CONFIG = new AuthConfig()
    private Logger logger = Mock(Logger)

    def "AuthLocator works with empty config"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-empty.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('unauthenticated.registry.org/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        !config.getUsername()
        !config.getPassword()
        allConfigs.configs.isEmpty()
        0 * logger.error(*_)
    }

    def "AuthLocator works using store"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-with-store.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        allConfigs.configs.size() == 1
        allConfigs.configs.get("url") == config
        0 * logger.error(*_)
    }

    def "AuthLocator works using helper and empty auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-empty-auth-with-helper.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'url'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        allConfigs.configs.size() == 1
        allConfigs.configs.get("url") == config
        0 * logger.error(*_)
    }

    def "AuthLocator works when helper returns no server URL (#955)"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-with-store-no-server-url.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'registry.example.com'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        allConfigs.configs.size() == 1
        allConfigs.configs.get("registry.example.com") == config
        0 * logger.error(*_)
    }

    def "AuthLocator works using auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-auth.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('container-registry.cloud.yandex.net/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        !config.getAuth()
        allConfigs.configs.size() == 1
        allConfigs.configs.get("container-registry.cloud.yandex.net") == config
        0 * logger.error(*_)
    }

    def "AuthLocator works using helper and existing auth"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-existing-auth-with-helper.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'https://registry.example.com'
        config.getUsername() == "username"
        config.getPassword() == "secret"
        config.getEmail() == 'not@val.id'
        !config.getAuth()
        allConfigs.configs.size() == 1
        allConfigs.configs.get(config.registryAddress) == config
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config for Docker Desktop config without existing credentials"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-docker-desktop.json', false)

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config == DEFAULT_AUTH_CONFIG
        allConfigs.configs.isEmpty()
        4 * logger.error(*_)
    }

    def "AuthLocator works for Docker Desktop config without existing credentials"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-docker-desktop.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        config.getUsername() == 'mac_user'
        config.getPassword() == 'XXX'
        allConfigs.configs.size() == 1
        allConfigs.configs.get(config.registryAddress) == config
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config when the file does not exist"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForMissingConfigFile('missing-file.json')
        locator.setLogger(logger)

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config == DEFAULT_AUTH_CONFIG
        allConfigs.configs.isEmpty()
        1 * logger.debug('Looking up auth config for registry: registry.example.com')
        2 * logger.debug("RegistryAuthLocator has configFile: ${new File('missing-file.json').absolutePath} (does not exist) and commandPathPrefix: docker-credential-")
        0 * logger.error(*_)
    }

    def "AuthLocator returns default config when the file is invalid"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('invalid.json')
        locator.setLogger(logger)

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config == DEFAULT_AUTH_CONFIG
        allConfigs.configs.isEmpty()
        2 * logger.error(*_)
    }

    def "AuthLocator returns default config when the credentials tool is missing"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-missing-tool.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('registry.example.com/org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config == DEFAULT_AUTH_CONFIG
        allConfigs.configs.isEmpty()
        4 * logger.error(*_)
    }

    def "AuthLocator uses default config then store does not contain the credentials"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-auth-store.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config == DEFAULT_AUTH_CONFIG
        allConfigs.configs.isEmpty()
        0 * logger.error(*_)
        20 * logger.debug(*_)
    }

    def "AuthLocator defaults to Docker Hub auth if no registry is explicitly given"() {
        given:
        RegistryAuthLocator locator = createAuthLocatorForExistingConfigFile('config-docker-hub-auth.json')

        when:
        AuthConfig config = locator.lookupAuthConfigWithDefaultAuthConfig('org/repo')
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs()

        then:
        config.getRegistryAddress() == 'https://index.docker.io/v1/'
        config.getUsername() == 'username'
        config.getPassword() == 'secret'
        allConfigs.configs.size() == 1
        allConfigs.configs.get(config.registryAddress) == config
        0 * logger.error(*_)
    }

    private RegistryAuthLocator createAuthLocatorForExistingConfigFile(String configName, Boolean mockHelper = true) {
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
