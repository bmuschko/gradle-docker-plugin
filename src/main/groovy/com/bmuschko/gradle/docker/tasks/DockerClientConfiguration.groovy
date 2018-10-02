package com.bmuschko.gradle.docker.tasks

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class DockerClientConfiguration {
    String url
    File certPath
    String apiVersion
}
