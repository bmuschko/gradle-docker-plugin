package com.bmuschko.gradle.docker

final class TestConfiguration {
    private static final String DOCKER_HOST_SYS_PROP = 'dockerHost'
    private static final String DOCKER_CERT_PATH_SYS_PROP = 'dockerCertPath'
    private static final String DOCKER_PRIVATE_REGISTRY_URL_SYS_PROP = 'dockerPrivateRegistryUrl'

    private TestConfiguration() {}

    static String getDockerHost() {
        System.properties[DOCKER_HOST_SYS_PROP] ?: 'http://localhost:2375'
    }

    static File getDockerCertPath() {
        System.properties[DOCKER_CERT_PATH_SYS_PROP] ? new File(System.properties[DOCKER_CERT_PATH_SYS_PROP]) : null
    }

    static String getDockerPrivateRegistryUrl() {
        System.properties[DOCKER_PRIVATE_REGISTRY_URL_SYS_PROP] ?: 'http://localhost:5000'
    }

    static String getDockerPrivateRegistryDomain() {
        getDockerPrivateRegistryUrl() - 'http://' - 'https://'
    }
}
