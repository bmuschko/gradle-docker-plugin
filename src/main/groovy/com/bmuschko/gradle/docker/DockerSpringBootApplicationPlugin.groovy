package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.internal.MainClassFinder
import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Spring Boot application.
 * <p>
 * This plugin can be configured with the help of {@link DockerSpringBootApplication}.
 *
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplicationPlugin extends DockerConventionApplicationPlugin<DockerSpringBootApplication> {

    private static final String SPRING_BOOT_APP_ANNOTATION = 'org.springframework.boot.autoconfigure.SpringBootApplication'

    /**
     * The name of extension registered with type {@link DockerSpringBootApplication}.
     */
    public static final String SPRING_BOOT_APPLICATION_EXTENSION_NAME = 'springBootApplication'

    @Override
    protected DockerSpringBootApplication configureExtension(ObjectFactory objectFactory, DockerExtension dockerExtension) {
        ((ExtensionAware) dockerExtension).extensions.create(SPRING_BOOT_APPLICATION_EXTENSION_NAME, DockerSpringBootApplication, objectFactory)
    }

    @Override
    protected String findMainClassName(File classesDir) {
        MainClassFinder.findSingleMainClass(classesDir, SPRING_BOOT_APP_ANNOTATION)
    }
}
