package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile.CompositeExecInstruction
import groovy.transform.CompileStatic

/**
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplication {
    final CompositeExecInstruction exec = new CompositeExecInstruction()
    String baseImage = 'openjdk:jre-alpine'
    Set<Integer> ports
    String tag

    Integer[] getPorts() {
        return ports != null ? ports as Integer[] : [8080] as Integer[]
    }
}
