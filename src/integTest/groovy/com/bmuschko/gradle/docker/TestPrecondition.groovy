package com.bmuschko.gradle.docker

final class TestPrecondition {
    public static final String SERVER_URL = 'http://localhost:2375'
    public static final String PRIVATE_REGISTRY = 'localhost:5000'
    public static final boolean DOCKER_SERVER_INFO_URL_REACHABLE = isDockerServerInfoUrlReachable()
    public static final boolean DOCKER_PRIVATE_REGISTRY_REACHABLE = isPrivateDockerRegistryReachable()
    public static final boolean DOCKERHUB_CREDENTIALS_AVAILABLE = hasDockerHubCredentials()

    private TestPrecondition() {}

    private static boolean isDockerServerInfoUrlReachable() {
        isUrlReachable(new URL("$SERVER_URL/info"))
    }

    private static boolean isPrivateDockerRegistryReachable() {
        isUrlReachable(new URL("http://$PRIVATE_REGISTRY"))
    }

    private static boolean isUrlReachable(URL url) {
        try {
            HttpURLConnection connection = url.openConnection()
            connection.requestMethod = 'GET'
            connection.connectTimeout = 3000
            return connection.responseCode == HttpURLConnection.HTTP_OK
        }
        catch(IOException e) {
            return false
        }
    }

    private static boolean hasDockerHubCredentials() {
        File gradlePropsFile = new File(System.getProperty('user.home'), '.gradle/gradle.properties')

        if(!gradlePropsFile.exists()) {
            return false
        }

        Properties properties = new Properties()

        gradlePropsFile.withInputStream {
            properties.load(it)
        }

        properties['dockerHubUsername'] != null && properties['dockerHubPassword'] != null && properties['dockerHubEmail'] != null
    }
}
