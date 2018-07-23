package com.bmuschko.gradle.docker

final class TestPrecondition {
    public static final List<String> ALLOWED_PING_PROTOCOLS = ['tcp', 'http', 'https', 'unix']
    public static final boolean DOCKER_PRIVATE_REGISTRY_REACHABLE = isPrivateDockerRegistryReachable()
    public static final boolean DOCKERHUB_CREDENTIALS_AVAILABLE = hasDockerHubCredentials()

    private TestPrecondition() {}

    private static boolean isPrivateDockerRegistryReachable() {
        isUriReachable(new URI("${TestConfiguration.dockerPrivateRegistryUrl}".replaceFirst('tcp', 'http')), 'v2')
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
        Properties gradleProperties = readDockerHubCredentials()
        gradleProperties['dockerHubUsername'] != null && gradleProperties['dockerHubPassword'] != null && gradleProperties['dockerHubEmail'] != null
    }

    private static Properties readDockerHubCredentials() {
        File gradlePropsFile = new File(System.getProperty('user.home'), '.gradle/gradle.properties')

        if(!gradlePropsFile.exists()) {
            return new Properties()
        }

        Properties properties = new Properties()

        gradlePropsFile.withInputStream {
            properties.load(it)
        }

        properties
    }
}
