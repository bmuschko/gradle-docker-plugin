package com.bmuschko.gradle.docker

class DockerHubCredentials {
    String username
    String password

    boolean isAvailable() {
        username && password
    }
}
