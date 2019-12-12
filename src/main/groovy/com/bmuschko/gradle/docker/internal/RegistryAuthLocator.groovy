package com.bmuschko.gradle.docker.internal

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.github.dockerjava.api.model.AuthConfig
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

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
     * @param registryCredentials extension of type registryCredentials
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or empty object if
     * no credentials found
     */
    AuthConfig lookupAuthConfig(String image) {
        return lookupAuthConfig(image, new AuthConfig())
    }

    /**
     * Gets authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, gets the information from
     * the registryCredentials object
     * @param registryCredentials extension of type registryCredentials
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or default object if
     * no credentials found
     */
    AuthConfig lookupAuthConfig(String image,
                                DockerRegistryCredentials registryCredentials) {
        AuthConfig defaultConfig =  createAuthConfig(registryCredentials)
        return lookupAuthConfig(image, defaultConfig)
    }

    /**
     * Gets authorization information using $DOCKER_CONFIG/.docker/config.json file
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or default object if
     * no credentials found
     */
    AuthConfig lookupAuthConfig(String image, AuthConfig defaultAuthConfig) {
        if (isWindows()) {
            logger.debug('RegistryAuthLocator is not supported on Windows. ' +
                'Please help test or improve it and update ' +
                'https://github.com/bmuschko/gradle-docker-plugin/')
            return defaultAuthConfig
        }

        String repository = getRepository(image)

        logger.debug("Looking up auth config for repository: $repository")
        logger.debug("RegistryAuthLocator has configFile: $configFile.absolutePath (${configFile.exists() ? 'exists' : 'does not exist'}) and commandPathPrefix: $commandPathPrefix")

        if (!configFile.isFile()) {
            return defaultAuthConfig
        }

        try {
            Map<String, Object> config = slurper.parse(configFile) as Map<String, Object>

            AuthConfig existingAuthConfig = findExistingAuthConfig(config, repository)
            if (existingAuthConfig != null) {
                return existingAuthConfig
            }

            // auths is empty, using helper:
            AuthConfig helperAuthConfig = authConfigUsingHelper(config, repository)
            if (helperAuthConfig != null) {
                return helperAuthConfig
            }

            // no credsHelper to use, using credsStore:
            final AuthConfig storeAuthConfig = authConfigUsingStore(config, repository)
            if (storeAuthConfig != null) {
                return storeAuthConfig
            }

        } catch(Exception ex) {
            logger.error('Failure when attempting to lookup auth config ' +
                '(docker repository: {}, configFile: {}. ' +
                'Falling back to docker-java default behaviour',
                repository,
                configFile,
                ex)
        }
        defaultAuthConfig
    }

    AuthConfig createAuthConfig(DockerRegistryCredentials registryCredentials) {
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
     * Extract repository name from the image name
     * @param image the name of the docker image
     * @return docker repository name
     */
    private static String getRepository(String image) {
        final int slashIndex = image.indexOf('/');

        if (slashIndex == -1 ||
            (!image.substring(0, slashIndex).contains('.') &&
                !image.substring(0, slashIndex).contains(':') &&
                !image.substring(0, slashIndex).equals('localhost'))) {
            return ''
        } else {
            return image.substring(0, slashIndex);
        }
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
                AuthConfig authCfg = new JsonSlurper().parseText(authJson) as AuthConfig
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

        String data = runCommand(hostName, credentialHelperName)
        logger.debug('Credential helper response: {}', data)
        Map<String, String> helperResponse = slurper.parseText(data) as Map<String, String>
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

    private String runCommand(String hostName, String credentialHelperName) {
        logger.debug('Executing docker credential helper: {} to locate auth config for: {}',
            credentialHelperName, hostName)
        try {
            StringBuilder sOut = new StringBuilder()
            StringBuilder sErr = new StringBuilder()
            Process proc = "$credentialHelperName get".execute()
            proc.withWriter { Writer writer -> writer << hostName }
            proc.waitFor()
            proc.waitForProcessOutput(sOut, sErr)
            if (sErr.length() > 0) {
                logger.error("{} get: {}", credentialHelperName, sErr.toString())
            }
            return sOut.toString()
        } catch (Exception e) {
            logger.error('Failure running docker credential helper ({})', credentialHelperName)
            throw e
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty('os.name')
        return osName != null && osName.startsWith('Windows')
    }

    @PackageScope
    void setLogger(Logger logger) {
        this.logger = logger
    }
}
