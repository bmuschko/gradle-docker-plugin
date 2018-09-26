package com.bmuschko.gradle.docker.tasks

import groovy.transform.EqualsAndHashCode
import org.gradle.api.file.Directory

@EqualsAndHashCode
class DockerClientConfiguration {
    String url
    Directory certPath
    String apiVersion
}
