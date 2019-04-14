package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
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

    DockerSpringBootApplication(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:jre-alpine')
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        tag = objectFactory.property(String)
    }
}
