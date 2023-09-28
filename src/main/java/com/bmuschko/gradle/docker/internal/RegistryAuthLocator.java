package com.bmuschko.gradle.docker.internal;

import com.bmuschko.gradle.docker.DockerRegistryCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.AuthConfigurations;
import com.github.dockerjava.core.NameParser;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class to get credentials information from extension of type {@link DockerRegistryCredentials} or from {@code $DOCKER_CONFIG/.docker/config.json} file.
 * <p>
 * Supports auth token, credentials store and credentials helpers. Only Linux OS is supported at the moment. Returns default auth object if called on Windows.
 * <p>
 * The class is ported from the <a href="https://github.com/testcontainers/testcontainers-java">testcontainers-java</a> project (PR <a href="https://github.com/testcontainers/testcontainers-java/pull/729">729</a>).
 */
public class RegistryAuthLocator {

    private static final String DOCKER_CONFIG = "DOCKER_CONFIG";
    private static final String USER_HOME = "user.home";
    private static final String DOCKER_DIR = ".docker";
    private static final String CONFIG_JSON = "config.json";
    private static final String AUTH_SECTION = "auths";
    private static final String HELPERS_SECTION = "credHelpers";
    private static final String CREDS_STORE_SECTION = "credsStore";

    private static final String DEFAULT_HELPER_PREFIX = "docker-credential-";

    private Logger logger = Logging.getLogger(RegistryAuthLocator.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final File configFile;
    private final String commandPathPrefix;
    private final String helperSuffix;

    private final ExecOperations execOperations;

    private RegistryAuthLocator(ExecOperations execOperations, File configFile, String commandPathPrefix, String helperSuffix) {
        this.execOperations = execOperations;
        this.configFile = configFile;
        this.commandPathPrefix = commandPathPrefix;
        this.helperSuffix = helperSuffix;
    }

    private RegistryAuthLocator(ExecOperations execOperations, File configFile) {
        this(execOperations, configFile, DEFAULT_HELPER_PREFIX, "");
    }

    /**
     * Creates new instance
     */
    private RegistryAuthLocator(ExecOperations execOperations) {
        this(execOperations, new File(configLocation()), DEFAULT_HELPER_PREFIX, "");
    }

    /**
     * Gets authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, returns empty AuthConfig object
     *
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or empty object if
     * no credentials found
     */
    AuthConfig lookupAuthConfigWithDefaultAuthConfig(String image) {
        return lookupAuthConfigWithAuthConfig(image, new AuthConfig());
    }

    /**
     * Gets authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, gets the information from
     * the registryCredentials object
     *
     * @param registryCredentials extension of type registryCredentials
     * @param image               the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or default object if
     * no credentials found
     */
    public AuthConfig lookupAuthConfig(String image, DockerRegistryCredentials registryCredentials) {
        AuthConfig defaultConfig = createAuthConfig(registryCredentials);

        if (isProvidedByBuild(defaultConfig)) {
            return defaultConfig;
        }

        return lookupAuthConfigWithAuthConfig(image, defaultConfig);
    }

    /**
     * Checks if the complete authentication information has been configured by the build,
     * more specifically through {@link DockerRegistryCredentials} by using the plugin DSL
     * or the property on relevant custom tasks.
     * <p>
     * If configured in build, the provided credentials take precendence over Docker helper.
     *
     * @param defaultConfig Default config
     * @return Flag
     */
    private boolean isProvidedByBuild(AuthConfig defaultConfig) {
        return defaultConfig.getRegistryAddress() != null && defaultConfig.getUsername() != null && defaultConfig.getPassword() != null;
    }

    /**
     * Gets authorization information using $DOCKER_CONFIG/.docker/config.json file
     *
     * @param image the name of docker image the action to be authorized for
     * @return AuthConfig object with a credentials info or default object if
     * no credentials found
     */
    private AuthConfig lookupAuthConfigWithAuthConfig(String image, AuthConfig defaultAuthConfig) {
        AuthConfig authConfigForRegistry = lookupAuthConfigForRegistry(getRegistry(image));
        if (authConfigForRegistry != null) {
            return authConfigForRegistry;
        }
        return defaultAuthConfig;
    }

    private AuthConfig lookupAuthConfigForRegistry(String registry) {
        logger.debug("Looking up auth config for registry: " + registry);
        logger.debug("RegistryAuthLocator has configFile: " + configFile.getAbsolutePath() + " (" + (configFile.exists() ? "exists" : "does not exist") + ") and commandPathPrefix: " + commandPathPrefix);

        if (!configFile.isFile()) {
            return null;
        }

        try {
            Map<String, Object> config = objectMapper.readValue(configFile, Map.class);

            AuthConfig existingAuthConfig = findExistingAuthConfig(config, registry);
            if (existingAuthConfig != null) {
                return decodeAuth(existingAuthConfig);
            }

            // auths is empty, using helper:
            AuthConfig helperAuthConfig = authConfigUsingHelper(config, registry);
            if (helperAuthConfig != null) {
                return decodeAuth(helperAuthConfig);
            }

            // no credsHelper to use, using credsStore:
            final AuthConfig storeAuthConfig = authConfigUsingStore(config, registry);
            if (storeAuthConfig != null) {
                return decodeAuth(storeAuthConfig);
            }

        } catch (Exception ex) {
            logger.error("Failure when attempting to lookup auth config " +
                         "(docker registry: {}, configFile: {}). " +
                         "Falling back to docker-java default behaviour",
                    registry,
                    configFile,
                    ex);
        }
        return null;
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an empty AuthConfigurations object is returned
     *
     * @return AuthConfigurations object containing all authorization information,
     * or an empty object if not available
     */
    public AuthConfigurations lookupAllAuthConfigs() {
        AuthConfigurations authConfigurations = new AuthConfigurations();

        logger.debug("RegistryAuthLocator has configFile: " + configFile.getAbsolutePath() + " (" + (configFile.exists() ? "exists" : "does not exist") + ") and commandPathPrefix: " + commandPathPrefix);

        if (!configFile.isFile()) {
            return authConfigurations;
        }

        try {
            Set<String> registryAddresses = new HashSet<String>();
            Map<String, Object> config = objectMapper.readValue(configFile, Map.class);

            // Discover registry addresses from `auths` section
            Map<String, Object> authSectionRegistries = (Map<String, Object>) config.getOrDefault(AUTH_SECTION, new HashMap<>());
            logger.debug("Found registries in docker auths section: {}", authSectionRegistries.keySet());
            registryAddresses.addAll(authSectionRegistries.keySet());

            // Discover registry addresses from `credHelpers` section
            Map<String, Object> credHelperSectionRegistries = (Map<String, Object>) config.getOrDefault(HELPERS_SECTION, new HashMap<>());
            logger.debug("Found registries in docker credHelpers section: {}", credHelperSectionRegistries.keySet());
            registryAddresses.addAll(credHelperSectionRegistries.keySet());

            // Discover registry addresses from credentials helper
            Object credStoreSection = config.get(CREDS_STORE_SECTION);
            if (credStoreSection instanceof String) {
                String credStoreCommand = commandPathPrefix + credStoreSection + helperSuffix;

                logger.debug("Executing docker credential helper: {} to locate auth configs", credStoreCommand);
                String credStoreResponse = runCommand(List.of(credStoreCommand, "list"));
                logger.debug("Credential helper response: {}", credStoreResponse);
                Map<String, String> helperResponse = parseText(credStoreResponse, Map.class);
                if (helperResponse != null) {
                    logger.debug("Found registries in docker credential helper: {}", helperResponse.keySet());
                    registryAddresses.addAll(helperResponse.keySet());
                }
            }

            // Lookup authentication information for all discovered registry addresses
            for (String registryAddress : registryAddresses) {
                AuthConfig registryAuthConfig = lookupAuthConfigForRegistry(registryAddress);
                if (registryAuthConfig != null) {
                    authConfigurations.addConfig(registryAuthConfig);
                }
            }
        } catch (Exception ex) {
            logger.error("Failure when attempting to lookup auth config " + "(configFile: {}). " + "Falling back to docker-java default behaviour", configFile, ex);
        }
        return authConfigurations;
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an AuthConfigurations object containing only the passed registryCredentials is returned
     *
     * @param registryCredentials extension of type registryCredentials
     * @return AuthConfigurations object containing all authorization information (if available), and the registryCredentials
     */
    public AuthConfigurations lookupAllAuthConfigs(DockerRegistryCredentials registryCredentials) {
        return lookupAllAuthConfigs(createAuthConfig(registryCredentials));
    }

    /**
     * Gets all authorization information
     * using $DOCKER_CONFIG/.docker/config.json file
     * If missing, an AuthConfigurations object containing only the passed additionalAuthConfig is returned
     *
     * @param additionalAuthConfig An additional AuthConfig object to add to the discovered authorization information.
     * @return AuthConfigurations object containing all authorization information (if available), and the additionalAuthConfig
     */
    public AuthConfigurations lookupAllAuthConfigs(AuthConfig additionalAuthConfig) {
        AuthConfigurations allAuthConfigs = lookupAllAuthConfigs();
        if (isProvidedByBuild(additionalAuthConfig)) {
            allAuthConfigs.addConfig(additionalAuthConfig);
        }
        return allAuthConfigs;
    }

    private AuthConfig createAuthConfig(DockerRegistryCredentials registryCredentials) {
        AuthConfig authConfig = new AuthConfig();
        authConfig.withRegistryAddress(registryCredentials.getUrl().get());

        if (registryCredentials.getUsername().isPresent()) {
            authConfig.withUsername(registryCredentials.getUsername().get());
        }

        if (registryCredentials.getPassword().isPresent()) {
            authConfig.withPassword(registryCredentials.getPassword().get());
        }

        if (registryCredentials.getEmail().isPresent()) {
            authConfig.withEmail(registryCredentials.getEmail().get());
        }
        return authConfig;
    }

    /**
     * @return default location of the docker credentials config file
     */
    private static String configLocation() {
        String defaultDir = System.getProperty(USER_HOME) + File.separator + DOCKER_DIR;
        String dir = System.getenv().getOrDefault(DOCKER_CONFIG, defaultDir);
        return dir + File.separator + CONFIG_JSON;
    }

    /**
     * Extract registry name from the image name
     *
     * @param image the name of the docker image
     * @return docker registry name
     */
    public String getRegistry(String image) {
        final NameParser.ReposTag tag = NameParser.parseRepositoryTag(image);
        final NameParser.HostnameReposName repository = NameParser.resolveRepositoryName(tag.repos);
        return repository.hostname;
    }

    /**
     * Finds 'auth' section in the config json matching the given repository
     *
     * @param config     config json object
     * @param repository the name of the docker repository
     * @return auth object with a token if present or null otherwise
     */
    private AuthConfig findExistingAuthConfig(Map<String, Object> config, String repository) {
        Map.Entry<String, Object> entry = findAuthNode(config, repository);
        if (entry != null && entry.getValue() != null && entry.getValue() instanceof Map) {
            Map authMap = (Map) entry.getValue();
            if (authMap.size() > 0) {
                String authJson;
                try {
                    authJson = objectMapper.writeValueAsString(entry.getValue());
                } catch (JsonProcessingException e) {
                    throw new UncheckedIOException(e);
                }
                AuthConfig authCfg = parseText(authJson, AuthConfig.class);
                if (authCfg == null) {
                    return null;
                }
                return authCfg.withRegistryAddress(entry.getKey());
            }
        }
        logger.debug("No existing AuthConfig found");
        return null;
    }

    /**
     * Finds 'auth' node in the config json matching the given repository
     *
     * @param config     config json object
     * @param repository the name of the docker repository
     * @return auth json node if present or null otherwise
     */
    private static Map.Entry<String, Object> findAuthNode(Map<String, Object> config, String repository) {
        Map<String, Object> auths = (Map<String, Object>) config.get(AUTH_SECTION);
        if (auths != null && auths.size() > 0) {
            for (Map.Entry<String, Object> entry : auths.entrySet()) {
                if (entry.getKey().endsWith("://" + repository) || entry.getKey().equals(repository)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Checks 'credHelpers' section in the config json matching the given repository
     *
     * @param config     config json object
     * @param repository the name of the docker repository
     * @return auth object if present or null otherwise
     */
    private AuthConfig authConfigUsingHelper(Map<String, Object> config, String repository) {
        Map<String, Object> credHelpers = (Map<String, Object>) config.get(HELPERS_SECTION);
        if (credHelpers != null && credHelpers.size() > 0) {
            Object helperNode = credHelpers.get(repository);
            if (helperNode instanceof String) {
                String helper = (String) helperNode;
                return runCredentialProvider(repository, helper);
            }
        }
        logger.debug("No helper found in the {} section", HELPERS_SECTION);
        return null;
    }

    /**
     * Runs external credentials provider tool (e.g. docker-credential-gcloud)
     *
     * @param hostName   the name of the docker repository to get auth for
     * @param credHelper the suffix of the docker credential helper (e.g. gcloud)
     * @return auth object if present or null otherwise
     */
    private AuthConfig runCredentialProvider(final String hostName, String credHelper) {
        String credentialHelperName = commandPathPrefix + credHelper + helperSuffix;

        logger.debug("Executing docker credential helper: {} to locate auth config for: {}", credentialHelperName, hostName);
        String data = runCommand(List.of(credentialHelperName, "get"), hostName);
        logger.debug("Credential helper response: {}", data);
        Map<String, String> helperResponse = parseText(data, Map.class);
        if (helperResponse == null) {
            return null;
        }

        logger.debug("Credential helper provided auth config for: {}", hostName);

        // If the ServerURL field is not returned by the helper, fall back to the provided hostname
        String registryAddress = helperResponse.get("ServerURL") != null ? helperResponse.get("ServerURL") : hostName;

        return new AuthConfig().withRegistryAddress(registryAddress).withUsername(helperResponse.get("Username")).withPassword(helperResponse.get("Secret"));
    }

    /**
     * Checks 'credsStore' section in the config json matching the given repository
     *
     * @param config     config json object
     * @param repository the name of the docker repository
     * @return auth object if present or null otherwise
     */
    private AuthConfig authConfigUsingStore(Map<String, Object> config, String repository) {
        Object credsStoreNode = config.get(CREDS_STORE_SECTION);
        if (credsStoreNode instanceof String) {
            String credsStore = (String) credsStoreNode;
            return runCredentialProvider(repository, credsStore);
        }
        logger.debug("No helper found in the {} section", CREDS_STORE_SECTION);
        return null;
    }

    private String runCommand(List<String> command) {
        return runCommand(command, null);
    }

    private String runCommand(List<String> command, String input) {
        try {
            ByteArrayOutputStream sOut = new ByteArrayOutputStream();
            ByteArrayOutputStream eOut = new ByteArrayOutputStream();

            execOperations.exec(execSpec -> {
                execSpec.setCommandLine(command);
                execSpec.setStandardOutput(sOut);
                execSpec.setErrorOutput(eOut);
                execSpec.setIgnoreExitValue(true);
                if (input != null) {
                    execSpec.setStandardInput(new ByteArrayInputStream(input.getBytes()));
                }
            });

            if (eOut.size() > 0) {
                logger.error("{}: {}", command, eOut.toString());
            }

            return sOut.toString();
        } catch (Exception e) {
            logger.error("Failure running command ({})", command);
            throw e;
        }

    }

    private static AuthConfig decodeAuth(AuthConfig config) {
        if (config.getAuth() == null) {
            return config;
        }

        String str = new String(Base64.getDecoder().decode(config.getAuth()), StandardCharsets.UTF_8);
        String[] parts = str.split(":", 2);
        if (parts.length != 2) {
            throw new RuntimeException("Invalid auth configuration file");
        }

        config.withUsername(parts[0]);
        config.withPassword(parts[1]);
        config.withAuth(null);
        return config;
    }

    private <T> T parseText(String data, Class<T> valueType) {
        try {
            return objectMapper.readValue(data, valueType);
        } catch (Exception e) {
            logger.debug("Failure parsing the json response {}", data, e);
            return null;
        }
    }

    void setLogger(Logger logger) {
        this.logger = logger;
    }

    public static class Factory {
        private final ExecOperations execOperations;
        @Inject
        public Factory(ExecOperations execOperations) {
            this.execOperations = execOperations;
        }

        RegistryAuthLocator withConfigAndCommandPathPrefix(File configFile, String commandPathPrefix, String helperSuffix) {
            return new RegistryAuthLocator(execOperations, configFile, commandPathPrefix, helperSuffix);
        }

        RegistryAuthLocator withConfig(File configFile) {
            return new RegistryAuthLocator(execOperations, configFile);
        }

        /**
         * Creates new instance
         */
        public RegistryAuthLocator withDefaults() {
            return new RegistryAuthLocator(execOperations);
        }
    }
}
