package com.bmuschko.gradle.docker.internal

interface DockerConfigResolver {
    String getDefaultDockerUrl()
    File getDefaultDockerCert()
}