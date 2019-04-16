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
    /**
     * The Docker base image used for the Spring Boot application.
     * <p>
     * Defaults to {@code openjdk:jre-alpine}.
     */
    final Property<String> baseImage

    /**
     * The Docker image exposed ports.
     * <p>
     * Defaults to {@code [8080]}.
     */
    final ListProperty<Integer> ports

    /**
     * The tag used for the Docker image.
     * <p>
     * Defaults to {@code <project.group>/<applicationName>:<project.version>}.
     */
    final Property<String> tag

    /**
     * The JVM arguments used to start the Java program.
     *
     * @since 4.8.0
     */
    final ListProperty<String> jvmArgs

    DockerSpringBootApplication(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:jre-alpine')
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        tag = objectFactory.property(String)
        jvmArgs = objectFactory.listProperty(String).empty()
    }
}
