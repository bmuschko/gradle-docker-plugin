package com.bmuschko.gradle.docker

final class TestConfiguration {
    private static final String DOCKER_HOST_SYS_PROP = 'dockerServerUrl'
    private static final String DOCKER_CERT_PATH_SYS_PROP = 'dockerCertPath'
    private static final String DOCKER_PRIVATE_REGISTRY_URL_SYS_PROP = 'dockerPrivateRegistryUrl'
    private static final String DOCKER_PRIVATE_SECURE_REGISTRY_URL_SYS_PROP = 'dockerPrivateSecureRegistryUrl'

    private TestConfiguration() {}

    static String getDockerHost() {
        System.getProperty(DOCKER_HOST_SYS_PROP) ?: 'unix:///var/run/docker.sock'
    }

    static File getDockerCertPath() {
        System.getProperty(DOCKER_CERT_PATH_SYS_PROP) ? new File(System.properties[DOCKER_CERT_PATH_SYS_PROP]) : null
    }

    static String getDockerPrivateRegistryUrl() {
        System.getProperty(DOCKER_PRIVATE_REGISTRY_URL_SYS_PROP) ?: 'http://localhost:5000'
    }

    static String getDockerPrivateRegistryDomain() {
        getDockerPrivateRegistryUrl() - 'http://' - 'https://'
    }

    static String getDockerPrivateSecureRegistryUrl() {
        System.getProperty(DOCKER_PRIVATE_SECURE_REGISTRY_URL_SYS_PROP) ?: 'http://localhost:5001'
    }

    static String getDockerPrivateSecureRegistryDomain() {
        getDockerPrivateSecureRegistryUrl() - 'http://' - 'https://'
    }
}
