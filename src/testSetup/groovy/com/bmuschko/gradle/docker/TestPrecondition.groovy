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
        DockerHubCredentials credentials = readDockerHubCredentials()
        credentials.available
    }

    static DockerHubCredentials readDockerHubCredentials() {
        DockerHubCredentials credentials = readDockerHubCredentialsFromEnvVars()

        if (credentials.available) {
            return credentials
        }

        readDockerHubCredentialsFromGradleProperties()
    }

    private static DockerHubCredentials readDockerHubCredentialsFromEnvVars() {
        DockerHubCredentials credentials = new DockerHubCredentials()
        String username = System.getenv('DOCKER_HUB_USERNAME')
        String password = System.getenv('DOCKER_HUB_PASSWORD')
        credentials.username = username
        credentials.password = password
        credentials
    }

    private static DockerHubCredentials readDockerHubCredentialsFromGradleProperties() {
        DockerHubCredentials credentials = new DockerHubCredentials()
        File gradlePropsFile = new File(System.getProperty('user.home'), '.gradle/gradle.properties')

        if(gradlePropsFile.exists()) {
            Properties properties = new Properties()

            gradlePropsFile.withInputStream {
                properties.load(it)
            }

            credentials.username = properties['dockerHubUsername']
            credentials.password = properties['dockerHubPassword']
        }

        credentials
    }
}
