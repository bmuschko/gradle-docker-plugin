package com.bmuschko.gradle.docker.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.model.AuthConfig
import groovy.transform.CompileStatic
import org.apache.commons.lang.SystemUtils
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.util.concurrent.TimeUnit

@CompileStatic
class RegistryAuthLocator {

    private static final String DOCKER_CONFIG = 'DOCKER_CONFIG'
    private static final String USER_HOME = 'user.home'
    private static final String DOCKER_DIR = '.docker'
    private static final String CONFIG_JSON = 'config.json'
    private static final String AUTH_SECTION = 'auths'
    private static final String HELPERS_SECTION = 'credHelpers'
    private static final String CREDS_STORE_SECTION = 'credsStore'

    private static final String SERVER_URL = '/ServerURL'
    private static final String USER_NAME = '/Username'
    private static final String SECRET = '/Secret'

    private static final Logger log = Logging.getLogger(RegistryAuthLocator)
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

    private final AuthConfig defaultAuthConfig
    private final File configFile
    private String commandPathPrefix = ''

    RegistryAuthLocator(AuthConfig defaultAuthConfig) {
        this.defaultAuthConfig = defaultAuthConfig
        this.configFile = new File(configLocation())
    }

    AuthConfig lookupAuthConfig(String image) {
        if (SystemUtils.IS_OS_WINDOWS) {
            log.debug('RegistryAuthLocator is not supported on Windows. ' +
                'Please help test or improve it and update ' +
                'https://github.com/bmuschko/gradle-docker-plugin/')
            return defaultAuthConfig
        }

        String repository = getRepository(image)

        log.debug('Looking up auth config for repository: {}', repository)

        log.debug('RegistryAuthLocator has configFile: {} ({}) and ' +
            'commandPathPrefix: {}',
            configFile,
            configFile.exists() ? 'exists' : 'does not exist',
            commandPathPrefix)

        try {
            JsonNode config = OBJECT_MAPPER.readTree(configFile)

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
            log.error('Failure when attempting to lookup auth config ' +
                '(docker repository: {}, configFile: {}. ' +
                'Falling back to docker-java default behaviour',
                repository,
                configFile,
                ex)
        }
        defaultAuthConfig
    }

    private static String configLocation() {
        String defaultDir = System.getProperty(USER_HOME) + File.separator + DOCKER_DIR
        String dir = System.getenv().getOrDefault(DOCKER_CONFIG, defaultDir)
        dir + File.separator + CONFIG_JSON
    }

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

    private static AuthConfig findExistingAuthConfig(JsonNode config, String repository) {
        Map.Entry<String, JsonNode> entry = findAuthNode(config, repository)
        if (entry != null && entry.getValue() != null && entry.getValue().size() > 0) {
            return OBJECT_MAPPER
                .treeToValue(entry.getValue(), AuthConfig.class)
                .withRegistryAddress(entry.getKey())
        }
        log.debug('No existing AuthConfig found')
        return null
    }

    private static Map.Entry<String, JsonNode> findAuthNode(JsonNode config,
                                                            String repository) {
        JsonNode auths = config.get(AUTH_SECTION)
        if (auths != null && auths.size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> fields = auths.fields()
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next()
                if (entry.getKey().endsWith('://' + repository)) {
                    return entry
                }
            }
        }
        return null
    }

    private AuthConfig authConfigUsingHelper(JsonNode config, String repository)  {
        JsonNode credHelpers = config.get(HELPERS_SECTION)
        if (credHelpers != null && credHelpers.size() > 0) {
            JsonNode helperNode = credHelpers.get(repository)
            if (helperNode != null && helperNode.isTextual()) {
                String helper = helperNode.asText()
                return runCredentialProvider(repository, helper)
            }
        }
        log.debug('No helper found in the {} section', HELPERS_SECTION)
        return null
    }

    private AuthConfig runCredentialProvider(String hostName, String credHelper) {
        String credentialHelperName = commandPathPrefix + 'docker-credential-' + credHelper

        String data = runCommand(hostName, credentialHelperName)
        JsonNode helperResponse = OBJECT_MAPPER.readTree(data)
        log.debug('Credential helper provided auth config for: {}', hostName)

        return new AuthConfig()
            .withRegistryAddress(helperResponse.at(SERVER_URL).asText())
            .withUsername(helperResponse.at(USER_NAME).asText())
            .withPassword(helperResponse.at(SECRET).asText())
    }

    private AuthConfig authConfigUsingStore(JsonNode config, String repository) {
        JsonNode credsStoreNode = config.get(CREDS_STORE_SECTION)
        if (credsStoreNode != null && !credsStoreNode.isMissingNode() &&
                credsStoreNode.isTextual()) {
            String credsStore = credsStoreNode.asText()
            return runCredentialProvider(repository, credsStore)
        }
        log.debug('No helper found in the {} section', CREDS_STORE_SECTION)
        return null
    }

    private static String runCommand(String hostName, String credentialHelperName) {
        log.debug('Executing docker credential helper: {} to locate auth config for: {}',
            credentialHelperName, hostName)
        try {
            ProcessBuilder pb = new ProcessBuilder(credentialHelperName, 'get')
            Process process = pb.start()

            writeHostName(process, hostName)
            return readStdOut(process)
        } catch (Exception e) {
            log.error('Failure running docker credential helper ({})', credentialHelperName)
            throw e
        }
    }

    private static writeHostName(Process process, String hostName) {
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream()))
        writer.write(hostName)
        writer.close()
    }

    private static readStdOut(Process process) {
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream()))
        StringBuilder builder = new StringBuilder()
        String line
        while ( (line = reader.readLine()) != null) {
            builder.append(line)
            builder.append(System.getProperty("line.separator"))
        }
        return builder.toString().trim()
    }
}
