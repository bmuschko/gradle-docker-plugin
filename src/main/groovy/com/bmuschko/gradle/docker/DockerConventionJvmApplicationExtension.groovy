/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * The extension for configuring a conventional JVM Docker plugin.
 *
 * @since 5.2.0
 */
@CompileStatic
class DockerConventionJvmApplicationExtension {

    /**
     * The Docker base image used for Java application.
     * <p>
     * Defaults to {@code openjdk:11-jre-slim}.
     */
    final Property<String> baseImage

    /**
     * The maintainer of the image.
     * <p>
     * Defaults to the value of the system property {@code user.name}.
     */
    final Property<String> maintainer

    /**
     * The Docker image exposed ports.
     * <p>
     * Defaults to {@code [8080]}.
     */
    final ListProperty<Integer> ports

    /**
     * The images used for the build and push operation e.g. {@code vieux/apache:2.0}.
     * <p>
     * Defaults to {@code [<project.group>/<applicationName>:<project.version>]}.
     *
     * @since 6.0.0
     */
    final SetProperty<String> images

    /**
     * The JVM arguments used to start the Java program.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 4.8.0
     */
    final ListProperty<String> jvmArgs

    /**
     * The main class name to use for starting the application e.g. {@code com.bmuschko.app.Main}.
     * <p>
     * By default tries to automatically find the main class by scanning the classpath.
     * The value of this property takes precedence and circumvents classpath scanning.
     *
     * @since 6.1.0
     */
    final Property<String> mainClassName

    DockerConventionJvmApplicationExtension(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:11-jre-slim')
        maintainer = objectFactory.property(String)
        maintainer.set(System.getProperty('user.name'))
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        images = objectFactory.setProperty(String).empty()
        jvmArgs = objectFactory.listProperty(String).empty()
        mainClassName = objectFactory.property(String)
    }
}
