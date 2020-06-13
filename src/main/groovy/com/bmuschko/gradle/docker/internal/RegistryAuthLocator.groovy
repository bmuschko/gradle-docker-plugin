package com.bmuschko.gradle.docker.internal

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.AuthConfigurations
import com.github.dockerjava.core.NameParser
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.apache.commons.codec.binary.Base64
import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.nio.charset.StandardCharsets

/**
 * Utility class to get credentials information from extension of type {@see DockerRegistryCredentials} or from {@code $DOCKER_CONFIG/.docker/config.json} file.
 * <p>
 * Supports auth token, credentials store and credentials helpers. Only Linux OS is supported at the moment. Returns default auth object if called on Windows.
 * <p>
 * The class is ported from the <a href="https://github.com/testcontainers/testcontainers-java">testcontainers-java</a> project (PR <a href="https://github.com/testcontainers/testcontainers-java/pull/729">729</a>).
 */
@CompileStatic
class RegistryAuthLocator {

    private static final String DOCKER_CONFIG = 'DOCKER_CONFIG'
    private static final String USER_HOME = 'user.home'
    private static final String DOCKER_DIR = '.docker'
    private static final String CONFIG_JSON = 'config.json'
    private static final String AUTH_SECTION = 'auths'
    private static final String HELPERS_SECTION = 'credHelpers'
    private static final String CREDS_STORE_SECTION = 'credsStore'

    private static final String DEFAULT_HELPER_PREFIX = 'docker-credential-'

    private Logger logger = Logging.getLogger(RegistryAuthLocator)
    private final JsonSlurper slurper = new JsonSlurper()

    private final File configFile
    private final String commandPathPrefix

    RegistryAuthLocator(File configFile, String commandPathPrefix) {
        this.configFile = configFile
        this.commandPathPrefix = commandPathPrefix
    }

    RegistryAuthLocator(File configFile) {
        this(configFile, DEFAULT_HELPER_PREFIX)
    }

    /**
     * Creates new instance
     * @param defaultAuthConfig the auth config object to return
     * in case not credentials found
     */
    RegistryAuthLocator() {
        this(new File(configLocation()), DEFAULT_HELPER_PREFIX)
    }

    /**
     * Gets authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, returns empty AuthConfig object
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or empty object if
     * no credentials found
     */
    AuthConfig lookupAuthConfig(String image) {
        AuthConfig authConfigForRepository = lookupAuthConfigForRegistry(getRegistry(image))
        return authConfigForRepository ?: new AuthConfig()
    }

    /**
     * Gets authorization information
     * using the registryCredentials object
     * If missing, gets the information from
     * $DOCKER_CONFIG/.docker/config.json file
     * @param registryCredentials extension of type registryCredentials
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or default object if
     * no credentials found
     */
    AuthConfig lookupAuthConfig(String image,
                                DockerRegistryCredentials registryCredentials) {
        String registry = getRegistry(image)
        String registryUrl = registryCredentials.getUrl().getOrElse("")
        if (registryUrl.endsWith('://' + registry) || registryUrl == registry) {
            return createAuthConfig(registryCredentials)
        }
        return lookupAuthConfig(image)
    }

    private AuthConfig lookupAuthConfigForRegistry(String registry) {
        if (isWindows()) {
            logger.debug('RegistryAuthLocator is not supported on Windows. ' +
                'Please help test or improve it and update ' +
                'https://github.com/bmuschko/gradle-docker-plugin/')
            return null
        }

        logger.debug("Looking up auth config for registry: $registry")
        logger.debug("RegistryAuthLocator has configFile: $configFile.absolutePath (${configFile.exists() ? 'exists' : 'does not exist'}) and commandPathPrefix: $commandPathPrefix")

        if (!configFile.isFile()) {
            return null
        }

        try {
            Map<String, Object> config = slurper.parse(configFile) as Map<String, Object>

            AuthConfig existingAuthConfig = findExistingAuthConfig(config, registry)
            if (existingAuthConfig != null) {
                return decodeAuth(existingAuthConfig)
            }

            // auths is empty, using helper:
            AuthConfig helperAuthConfig = authConfigUsingHelper(config, registry)
            if (helperAuthConfig != null) {
                return decodeAuth(helperAuthConfig)
            }

            // no credsHelper to use, using credsStore:
            final AuthConfig storeAuthConfig = authConfigUsingStore(config, registry)
            if (storeAuthConfig != null) {
                return decodeAuth(storeAuthConfig)
            }

        } catch(Exception ex) {
            logger.error('Failure when attempting to lookup auth config ' +
                '(docker registry: {}, configFile: {}). ' +
                'Falling back to docker-java default behaviour',
                registry,
                configFile,
                ex)
        }
        return null
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an empty AuthConfigurations object is returned
     * @return AuthConfigurations object containing all authorization information,
     * or an empty object if not available
     */
    AuthConfigurations lookupAllAuthConfigs() {
        AuthConfigurations authConfigurations = new AuthConfigurations()
        if (isWindows()) {
            logger.debug('RegistryAuthLocator is not supported on Windows. ' +
                'Please help test or improve it and update ' +
                'https://github.com/bmuschko/gradle-docker-plugin/')
            return authConfigurations
        }

        logger.debug("RegistryAuthLocator has configFile: $configFile.absolutePath (${configFile.exists() ? 'exists' : 'does not exist'}) and commandPathPrefix: $commandPathPrefix")

        if (!configFile.isFile()) {
            return authConfigurations
        }

        try {
            Set<String> registryAddresses = new HashSet<>()
            Map<String, Object> config = slurper.parse(configFile) as Map<String, Object>

            // Discover registry addresses from `auths` section
            Map<String, Object> authSectionRegistries = config.getOrDefault(AUTH_SECTION, new HashMap()) as Map<String, Object>
            logger.debug("Found registries in docker auths section: {}", authSectionRegistries.keySet())
            registryAddresses.addAll(authSectionRegistries.keySet())

            // Discover registry addresses from `credHelpers` section
            Map<String, Object> credHelperSectionRegistries = config.getOrDefault(HELPERS_SECTION, new HashMap()) as Map<String, Object>
            logger.debug("Found registries in docker credHelpers section: {}", credHelperSectionRegistries.keySet())
            registryAddresses.addAll(credHelperSectionRegistries.keySet())

            // Discover registry addresses from credentials helper
            Object credStoreSection = config.get(CREDS_STORE_SECTION)
            if (credStoreSection != null && credStoreSection instanceof String) {
                String credStoreCommand = commandPathPrefix + credStoreSection

                logger.debug('Executing docker credential helper: {} to locate auth configs', credStoreCommand)
                String credStoreResponse = runCommand("$credStoreCommand list")
                logger.debug('Credential helper response: {}', credStoreResponse)
                Map<String, String> helperResponse = parseText(credStoreResponse) as Map<String, String>
                if (helperResponse != null) {
                    logger.debug("Found registries in docker credential helper: {}", helperResponse.keySet())
                    registryAddresses.addAll(helperResponse.keySet())
                }
            }

            // Lookup authentication information for all discovered registry addresses
            for (String registryAddress : registryAddresses) {
                AuthConfig registryAuthConfig = lookupAuthConfigForRegistry(registryAddress)
                if (registryAuthConfig != null) {
                    authConfigurations.addConfig(registryAuthConfig)
                }
            }
        } catch (Exception ex) {
            logger.error('Failure when attempting to lookup auth config ' +
                '(configFile: {}). ' +
                'Falling back to docker-java default behaviour',
                configFile,
                ex)
        }
        return authConfigurations
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an AuthConfigurations object containing only the passed registryCredentials is returned
     * @param registryCredentials extension of type registryCredentials
     * @return AuthConfigurations object containing all authorization information (if available), and the registryCredentials
     */
    AuthConfigurations lookupAllAuthConfigs(DockerRegistryCredentials registryCredentials) {
        return lookupAllAuthConfigs(createAuthConfig(registryCredentials))
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an AuthConfigurations object containing only the passed additionalAuthConfig is returned
     * @param additionalAuthConfig An additional AuthConfig object to add to the discovered authorization information.
     * @return AuthConfigurations object containing all authorization information (if available), and the additionalAuthConfig
     */
    AuthConfigurations lookupAllAuthConfigs(AuthConfig additionalAuthConfig) {
        AuthConfigurations allAuthConfigs = lookupAllAuthConfigs()
        allAuthConfigs.addConfig(additionalAuthConfig)
        return allAuthConfigs
    }

    private AuthConfig createAuthConfig(DockerRegistryCredentials registryCredentials) {
        AuthConfig authConfig = new AuthConfig()
        authConfig.withRegistryAddress(registryCredentials.url.get())

        if (registryCredentials.username.isPresent()) {
            authConfig.withUsername(registryCredentials.username.get())
        }

        if (registryCredentials.password.isPresent()) {
            authConfig.withPassword(registryCredentials.password.get())
        }

        if (registryCredentials.email.isPresent()) {
            authConfig.withEmail(registryCredentials.email.get())
        }
        authConfig
    }

    /**
     * @return default location of the docker credentials config file
     */
    private static String configLocation() {
        String defaultDir = System.getProperty(USER_HOME) + File.separator + DOCKER_DIR
        String dir = System.getenv().getOrDefault(DOCKER_CONFIG, defaultDir)
        dir + File.separator + CONFIG_JSON
    }

    /**
     * Extract registry name from the image name
     * @param image the name of the docker image
     * @return docker registry name
     */
    private static String getRegistry(String image) {
        final NameParser.ReposTag tag = NameParser.parseRepositoryTag(image)
        final NameParser.HostnameReposName repository = NameParser.resolveRepositoryName(tag.repos)
        return repository.hostname
    }

    /**
     * Finds 'auth' section in the config json matching the given repository
     * @param config config json object
     * @param repository the name of the docker repository
     * @return auth object with a token if present or null otherwise
     */
    private AuthConfig findExistingAuthConfig(Map<String, Object> config, String repository) {
        Map.Entry<String, Object> entry = findAuthNode(config, repository)
        if (entry != null && entry.getValue() != null && entry.getValue() instanceof Map) {
            Map authMap = entry.getValue() as Map
            if (authMap.size() > 0) {
                String authJson = JsonOutput.toJson(entry.getValue())
                AuthConfig authCfg = parseText(authJson) as AuthConfig
                if (authCfg == null) {
                    return null
                }
                return authCfg.withRegistryAddress(entry.getKey())
            }
        }
        logger.debug('No existing AuthConfig found')
        return null
    }

    /**
     * Finds 'auth' node in the config json matching the given repository
     * @param config config json object
     * @param repository the name of the docker repository
     * @return auth json node if present or null otherwise
     */
    private static Map.Entry<String, Object> findAuthNode(Map<String, Object> config,
                                                            String repository) {
        Map<String, Object> auths = config.get(AUTH_SECTION) as Map<String, Object>
        if (auths != null && auths.size() > 0) {
            for (Map.Entry<String, Object> entry: auths.entrySet() ) {
                if (entry.getKey().endsWith('://' + repository) || entry.getKey() == repository) {
                    return entry
                }
            }
        }
        return null
    }

    /**
     * Checks 'credHelpers' section in the config json matching the given repository
     * @param config config json object
     * @param repository the name of the docker repository
     * @return auth object if present or null otherwise
     */
    private AuthConfig authConfigUsingHelper(Map<String, Object> config, String repository)  {
        Map<String, Object> credHelpers = config.get(HELPERS_SECTION) as Map<String, Object>
        if (credHelpers != null && credHelpers.size() > 0) {
            Object helperNode = credHelpers.get(repository)
            if (helperNode != null && helperNode instanceof String) {
                String helper = helperNode as String
                return runCredentialProvider(repository, helper)
            }
        }
        logger.debug('No helper found in the {} section', HELPERS_SECTION)
        return null
    }

    /**
     * Runs external credentials provider tool (e.g. docker-credential-gcloud)
     * @param hostName the name of the docker repository to get auth for
     * @param credHelper the suffix of the docker credential helper (e.g. gcloud)
     * @return auth object if present or null otherwise
     */
    private AuthConfig runCredentialProvider(String hostName, String credHelper) {
        String credentialHelperName = commandPathPrefix + credHelper

        logger.debug('Executing docker credential helper: {} to locate auth config for: {}',
            credentialHelperName, hostName)
        String data = runCommand("$credentialHelperName get", { Writer writer -> writer << hostName })
        logger.debug('Credential helper response: {}', data)
        Map<String, String> helperResponse = parseText(data) as Map<String, String>
        if (helperResponse == null) {
            return null
        }
        logger.debug('Credential helper provided auth config for: {}', hostName)

        return new AuthConfig()
            .withRegistryAddress(helperResponse.ServerURL)
            .withUsername(helperResponse.Username)
            .withPassword(helperResponse.Secret)
    }

    /**
     * Checks 'credsStore' section in the config json matching the given repository
     * @param config config json object
     * @param repository the name of the docker repository
     * @return auth object if present or null otherwise
     */
    private AuthConfig authConfigUsingStore(Map<String, Object> config, String repository) {
        Object credsStoreNode = config.get(CREDS_STORE_SECTION)
        if (credsStoreNode != null && credsStoreNode instanceof String) {
            String credsStore = credsStoreNode as String
            return runCredentialProvider(repository, credsStore)
        }
        logger.debug('No helper found in the {} section', CREDS_STORE_SECTION)
        return null
    }

    private String runCommand(String command) {
        runCommand(command, null);
    }

    private String runCommand(String command, Action<Writer> writerAction) {
        try {
            StringBuilder sOut = new StringBuilder()
            StringBuilder sErr = new StringBuilder()
            Process proc = "$command".execute()
            if (writerAction != null) {
                proc.withWriter { Writer writer -> writerAction.execute(writer) }
            }
            proc.waitFor()
            proc.waitForProcessOutput(sOut, sErr)
            if (sErr.length() > 0) {
                logger.error("{}: {}", command, sErr.toString())
            }
            return sOut.toString()
        } catch (Exception e) {
            logger.error('Failure running command ({})', command)
            throw e
        }

    }

    private static boolean isWindows() {
        String osName = System.getProperty('os.name')
        return osName != null && osName.startsWith('Windows')
    }

    private static AuthConfig decodeAuth(AuthConfig config) {
        if (config.getAuth() == null) {
            return config
        }

        String str = new String(Base64.decodeBase64(config.getAuth()), StandardCharsets.UTF_8)
        String[] parts = str.split(":", 2)
        if (parts.length != 2) {
            throw new IOException("Invalid auth configuration file")
        }
        config.withUsername(parts[0])
        config.withPassword(parts[1])
        config.withAuth(null)
        return config
    }

    private Object parseText(String data) {
        try {
            return slurper.parseText(data)
        } catch (Exception e) {
            logger.debug('Failure parsing the json response {}', data, e)
            return null
        }
    }

    @PackageScope
    void setLogger(Logger logger) {
        this.logger = logger
    }
}
