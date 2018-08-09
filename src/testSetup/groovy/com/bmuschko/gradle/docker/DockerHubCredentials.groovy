package com.bmuschko.gradle.docker

class DockerHubCredentials {
    String username
    String password
    String email

    boolean isAvailable() {
        username && password && email
    }
}
