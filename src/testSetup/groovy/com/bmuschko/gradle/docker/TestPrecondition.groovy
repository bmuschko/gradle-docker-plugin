package com.bmuschko.gradle.docker

import java.nio.charset.StandardCharsets

final class TestPrecondition {

    public static final String USER = "testuser"
    public static final String PASSWD = "testpassword"

    public static final List<String> ALLOWED_PING_PROTOCOLS = ['tcp', 'http', 'https', 'unix']
    public static final boolean DOCKER_PRIVATE_REGISTRY_REACHABLE =
        isPrivateDockerRegistryReachable(TestConfiguration.dockerPrivateRegistryUrl)
    public static final boolean DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE =
        isPrivateDockerRegistryReachable(TestConfiguration.dockerPrivateSecureRegistryUrl)
    public static final boolean DOCKER_HUB_CREDENTIALS_AVAILABLE = hasDockerHubCredentials()
    public static final boolean HARBOR_CREDENTIALS_AVAILABLE = hasHarborCredentials()

    private TestPrecondition() {}

    private static boolean isPrivateDockerRegistryReachable(String config) {
        isUriReachable(new URI("${config}".replaceFirst('tcp', 'http')), 'v2')
    }

    private static boolean isUriReachable(URI dockerUri, String extraPath) {
        if(!ALLOWED_PING_PROTOCOLS.contains(dockerUri.scheme)) {
            throw new IllegalArgumentException("Unsupported URI protocol '$dockerUri.scheme'")
        }

        if (dockerUri.scheme.startsWith('http')) {
            try {
                HttpURLConnection connection = ((extraPath != null) ? new URL(dockerUri.toString() + "/" + extraPath) : dockerUri).openConnection()
                connection.requestMethod = 'GET'
                connection.connectTimeout = 3000
                String encoded = Base64.getEncoder().encodeToString(("$USER:$PASSWD").getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic $encoded")
                return (connection.responseCode >= 200 && connection.responseCode <= 399)
            } catch(Exception e) {
                return false
            }
        } else {

            // TODO: should really use something like kohlschutter unix domain socket library
            // or netty to test if the socket is live and can be pinged.
            return new File(dockerUri.path).exists()
        }
    }

    private static boolean hasDockerHubCredentials() {
        RegistryCredentials credentials = readDockerHubCredentials()
        credentials.available
    }

    static RegistryCredentials readDockerHubCredentials() {
        RegistryCredentials credentials = readCredentialsFromEnvVars('DOCKER_HUB_USERNAME', 'DOCKER_HUB_PASSWORD')

        if (credentials.available) {
            return credentials
        }

        readCredentialsFromGradleProperties('dockerHubUsername', 'dockerHubPassword')
    }

    private static boolean hasHarborCredentials() {
        RegistryCredentials credentials = readHarborCredentials()
        credentials.available
    }

    static RegistryCredentials readHarborCredentials() {
        RegistryCredentials credentials = readCredentialsFromEnvVars('HARBOR_USERNAME', 'HARBOR_PASSWORD')

        if (credentials.available) {
            return credentials
        }

        readCredentialsFromGradleProperties('harborUsername', 'harborPassword')
    }

    private static RegistryCredentials readCredentialsFromEnvVars(String usernameKey, String passwordKey) {
        RegistryCredentials credentials = new RegistryCredentials()
        String username = System.getenv(usernameKey)
        String password = System.getenv(passwordKey)
        credentials.username = username
        credentials.password = password
        credentials
    }

    private static RegistryCredentials readCredentialsFromGradleProperties(String usernameKey, String passwordKey) {
        RegistryCredentials credentials = new RegistryCredentials()
        File gradlePropsFile = new File(System.getProperty('user.home'), '.gradle/gradle.properties')

        if(gradlePropsFile.exists()) {
            Properties properties = new Properties()

            gradlePropsFile.withInputStream {
                properties.load(it)
            }

            credentials.username = properties[usernameKey]
            credentials.password = properties[passwordKey]
        }

        credentials
    }
}
