package com.bmuschko.gradle.docker

class RegistryCredentials {
    String username
    String password

    boolean isAvailable() {
        username && password
    }
}
