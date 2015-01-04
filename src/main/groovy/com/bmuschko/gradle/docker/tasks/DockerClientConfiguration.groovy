package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.DockerRegistry

class DockerClientConfiguration {
    String url
    File certPath
    DockerRegistry registry
}
