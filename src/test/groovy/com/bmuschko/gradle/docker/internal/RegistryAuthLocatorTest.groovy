package com.bmuschko.gradle.docker.internal

import org.gradle.testfixtures.ProjectBuilder

import static com.bmuschko.gradle.docker.internal.OsUtils.isWindows;

import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.AuthConfigurations
import org.gradle.api.logging.Logger
import spock.lang.Issue
import spock.lang.Specification

class RegistryAuthLocatorTest extends Specification {

    private static final String CONFIG_LOCATION = '/auth-config/'
    private static final AuthConfig DEFAULT_AUTH_CONFIG = new AuthConfig()
    private final RegistryAuthLocator.Factory factory = ProjectBuilder.builder().build().objects.newInstance(RegistryAuthLocator.Factory)
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

    @Issue('https://github.com/bmuschko/gradle-docker-plugin/issues/955')
    def "AuthLocator works when helper returns no server URL"() {
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

    @Issue('https://github.com/bmuschko/gradle-docker-plugin/issues/985')
    def "AuthLocator returns empty auth from file over default"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-docker-hub-user-pass.json', false)

        when:
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs(new AuthConfig())

        then:
        AuthConfig config = new AuthConfig()
            .withUsername('username')
            .withPassword('secret')
        allConfigs.configs.size() == 1
        allConfigs.configs.get(config.registryAddress) == config
        0 * logger.error(*_)
    }

    @Issue('https://github.com/bmuschko/gradle-docker-plugin/issues/1179')
    def "AuthLocator returns valid auth from file and default"() {
        given:
        RegistryAuthLocator locator =
            createAuthLocatorForExistingConfigFile('config-docker-hub-user-pass.json', false)

        when:
        AuthConfigurations allConfigs = locator.lookupAllAuthConfigs(new AuthConfig()
            .withRegistryAddress("https://index.docker.io/v2/")
            .withUsername("username2")
            .withPassword("secret2"))

        then:
        AuthConfig config = new AuthConfig()
            .withUsername('username')
            .withPassword('secret')
        AuthConfig configAddon = new AuthConfig()
            .withRegistryAddress("https://index.docker.io/v2/")
            .withUsername('username2')
            .withPassword('secret2')
        allConfigs.configs.size() == 2
        allConfigs.configs.get(config.registryAddress) == config
        allConfigs.configs.get(configAddon.registryAddress) == configAddon

        0 * logger.error(*_)
    }

    def "AuthLocator returns correct default registry"() {
        given:
        String image = 'ubuntu'

        when:
        String registry = factory.withDefaults().getRegistry(image)

        then:
        registry == 'https://index.docker.io/v1/'
    }


    def "AuthLocator returns correct custom registry"() {
        given:
        String image = 'gcr.io/distroless/java17'

        when:
        String registry = factory.withDefaults().getRegistry(image)

        then:
        registry == 'gcr.io'
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
            String command = new File(configFile.getParentFile(), 'docker-credential-').getAbsolutePath()
            String helperSuffix
            if (isWindows()) {
                helperSuffix = '.bat'
            } else {
                helperSuffix = ''
            }
            locator = factory.withConfigAndCommandPathPrefix(configFile, command, helperSuffix)
        } else {
            locator = factory.withConfig(configFile)
        }
        locator.setLogger(logger)
        locator
    }

    private RegistryAuthLocator createAuthLocatorForMissingConfigFile(String configName) {
        RegistryAuthLocator locator = factory.withConfig(new File(configName))
        locator.setLogger(logger)
        locator
    }
}
