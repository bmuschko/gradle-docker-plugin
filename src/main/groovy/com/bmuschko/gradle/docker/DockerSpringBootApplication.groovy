package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplication {
    final Property<String> baseImage
    final ListProperty<Integer> ports
    final Property<String> tag

    DockerSpringBootApplication(Project project) {
        baseImage = project.objects.property(String)
        baseImage.set('openjdk:jre-alpine')
        ports = project.objects.listProperty(Integer)
        ports.set([8080])
        tag = project.objects.property(String)
    }
}
