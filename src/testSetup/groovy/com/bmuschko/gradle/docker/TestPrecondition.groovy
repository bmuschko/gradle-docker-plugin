package com.bmuschko.gradle.docker

final class TestPrecondition {
    public static final List<String> ALLOWED_PING_PROTOCOLS = ['http', 'https']
    public static final boolean DOCKER_SERVER_INFO_URL_REACHABLE = isDockerServerInfoUrlReachable()
    public static final boolean DOCKER_PRIVATE_REGISTRY_REACHABLE = isPrivateDockerRegistryReachable()
    public static final boolean DOCKERHUB_CREDENTIALS_AVAILABLE = hasDockerHubCredentials()

    private TestPrecondition() {}

    private static boolean isDockerServerInfoUrlReachable() {
        isUrlReachable(new URL("${TestConfiguration.dockerHost}/info"))
    }

    private static boolean isPrivateDockerRegistryReachable() {
        isUrlReachable(new URL(TestConfiguration.dockerPrivateRegistryUrl))
    }

    private static boolean isUrlReachable(URL url) {
        if(!ALLOWED_PING_PROTOCOLS.contains(url.protocol)) {
            throw new IllegalArgumentException("Unsupported URL protocol '$url.protocol'")
        }

        try {
            HttpURLConnection connection = url.openConnection()
            connection.requestMethod = 'GET'
            connection.connectTimeout = 3000
            return connection.responseCode == HttpURLConnection.HTTP_OK
        }
        catch(Exception e) {
            return false
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
