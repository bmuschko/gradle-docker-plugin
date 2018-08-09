package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic

/**
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplication {
    String baseImage = 'openjdk:jre-alpine'
    Set<Integer> ports
    String tag

    Integer[] getPorts() {
        return ports != null ? ports as Integer[] : [8080] as Integer[]
    }
}
