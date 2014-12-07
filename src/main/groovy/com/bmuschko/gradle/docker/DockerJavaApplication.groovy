package com.bmuschko.gradle.docker

class DockerJavaApplication {
    String baseImage = 'java'
    String maintainer
    Integer port = 8080
    String tag
}
