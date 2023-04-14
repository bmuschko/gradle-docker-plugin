package com.bmuschko.gradle.docker;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import java.util.List;

/**
 * The extension for configuring a conventional JVM Docker plugin.
 *
 * @since 5.2.0
 */
public class DockerConventionJvmApplicationExtension {

    /**
     * The Docker base image used for Java application.
     * <p>
     * Defaults to {@code openjdk:11.0.16-jre-slim}.
     */
    public final Property<String> getBaseImage() {
        return baseImage;
    }

    private final Property<String> baseImage;

    /**
     * The maintainer of the image.
     * <p>
     * Defaults to the value of the system property {@code user.name}.
     */
    public final Property<String> getMaintainer() {
        return maintainer;
    }

    private final Property<String> maintainer;

    /**
     * The username (or UID) and optionally the user group (or GID) to use as the default user and group for the container, e.g. {@code johndoe:30000}.
     * <p>
     * Defaults to not setting a user. Usually that means running with the {@code root} user.
     *
     * @since 9.3.0
     */
    public final Property<String> getUser() {
        return user;
    }

    private final Property<String> user;

    /**
     * The Docker image exposed ports.
     * <p>
     * Defaults to {@code [8080]}.
     */
    public final ListProperty<Integer> getPorts() {
        return ports;
    }

    private final ListProperty<Integer> ports;

    /**
     * The images used for the build and push operation e.g. {@code vieux/apache:2.0}.
     * <p>
     * Defaults to {@code [<project.group>/<applicationName>:<project.version>]}.
     *
     * @since 6.0.0
     */
    public final SetProperty<String> getImages() {
        return images;
    }

    private final SetProperty<String> images;

    /**
     * The JVM arguments used to start the Java program.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 4.8.0
     */
    public final ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    private final ListProperty<String> jvmArgs;

    /**
     * The main class name to use for starting the application e.g. {@code com.bmuschko.app.Main}.
     * <p>
     * By default tries to automatically find the main class by scanning the classpath.
     * The value of this property takes precedence and circumvents classpath scanning.
     *
     * @since 6.1.0
     */
    public final Property<String> getMainClassName() {
        return mainClassName;
    }

    private final Property<String> mainClassName;

    /**
     * The program arguments appended to Java application.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 9.0.0
     */
    public final ListProperty<String> getArgs() {
        return args;
    }

    private final ListProperty<String> args;

    public DockerConventionJvmApplicationExtension(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String.class);
        baseImage.convention("openjdk:11.0.16-jre-slim");
        maintainer = objectFactory.property(String.class);
        maintainer.convention(System.getProperty("user.name"));
        user = objectFactory.property(String.class);
        ports = objectFactory.listProperty(Integer.class);
        ports.convention(List.of(8080));
        images = objectFactory.setProperty(String.class);
        jvmArgs = objectFactory.listProperty(String.class);
        mainClassName = objectFactory.property(String.class);
        args = objectFactory.listProperty(String.class);
    }
}
