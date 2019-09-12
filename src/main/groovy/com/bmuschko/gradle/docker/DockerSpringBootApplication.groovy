package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * The extension for configuring a Spring Boot application via the {@link DockerSpringBootApplicationPlugin}.
 * <p>
 * Enhances the extension {@link DockerExtension} as child DSL element.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     springBootApplication {
 *         baseImage = 'openjdk:8-alpine'
 *         maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
 *         ports = [9090, 8080]
 *         tag = 'awesome-spring-boot:1.115'
 *         jvmArgs = ['-Dspring.profiles.active=production', '-Xmx2048m']
 *     }
 * }
 * </pre>
 *
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
     * The maintainer of the image.
     * <p>
     * Defaults to the value of the system property {@code user.name}.
     *
     * @since 4.9.0
     */
    final Property<String> maintainer

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
     * @deprecated use {@link #tags} - will be removed in 6.0.0
     */
    @Deprecated
    final Property<String> tag

    /**
     * The tags used for the Docker image.
     * <p>
     * Defaults to {@code [<project.group>/<applicationName>:<project.version>]}.
     */
    final ListProperty<String> tags

    /**
     * Tags that will be used for building the Docker image, but will not be pushed to the repo.
     * In order for these tags to be built, they should be specified in the {@link #tags} property.
     * e.g. for rapid local development, "busybox:latest" would be useful, but becomes confusing when
     * pushed to a remote repo
     * <p>
     * Defaults to {@code []}
     */
    final ListProperty<String> localOnlyTags

    /**
     * The JVM arguments used to start the Java program.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 4.8.0
     */
    final ListProperty<String> jvmArgs

    DockerSpringBootApplication(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:jre-alpine')
        maintainer = objectFactory.property(String)
        maintainer.set(System.getProperty('user.name'))
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        tag = objectFactory.property(String)
        tags = objectFactory.listProperty(String)
        localOnlyTags = objectFactory.listProperty(String).empty()
        jvmArgs = objectFactory.listProperty(String).empty()
    }
}
