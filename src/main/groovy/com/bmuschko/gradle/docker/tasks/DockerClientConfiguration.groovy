package com.bmuschko.gradle.docker.tasks

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.gradle.api.file.Directory

@EqualsAndHashCode
@CompileStatic
class DockerClientConfiguration {
    String url
    Directory certPath
    String apiVersion
    Long httpConnectionTimeout;
    Long httpResponseTimeout;
}
