package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.Dockerfile.CompositeExecInstruction
import groovy.transform.CompileStatic

@CompileStatic
class DockerSpringBootApplication {
    final CompositeExecInstruction exec = new CompositeExecInstruction()
    String baseImage = 'openjdk'
    Set<Integer> ports
    String tag

    Integer[] getPorts() {
        return ports != null ? ports as Integer[] : [8080] as Integer[]
    }

    Dockerfile.CompositeExecInstruction exec(@DelegatesTo(Dockerfile.CompositeExecInstruction) Closure<Void> closure) {
        exec.clear()
        exec.apply(closure)
    }
}
