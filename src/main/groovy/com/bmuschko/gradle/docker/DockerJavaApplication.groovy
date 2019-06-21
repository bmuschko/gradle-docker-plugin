/*
 * Copyright 2014 the original author or authors.
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

/**
 * The extension for configuring a Java application via the {@link DockerJavaApplicationPlugin}.
 * <p>
 * Enhances the extension {@link DockerExtension} as child DSL element.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     javaApplication {
 *         baseImage = 'dockerfile/java:openjdk-7-jre'
 *         maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
 *         ports = [9090, 5701]
 *         tag = 'jettyapp:1.115'
 *         jvmArgs = ['-Xms256m', '-Xmx2048m']
 *    }
 * }
 * </pre>
 */
@CompileStatic
class DockerJavaApplication {

    /**
     * The Docker base image used for Java application.
     * <p>
     * Defaults to {@code openjdk:jre-alpine}.
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
     * The tags used for the Docker image.
     * <p>
     * Defaults to {@code [<project.group>/<applicationName>:<project.version>]}.
     */
    final ListProperty<String> tags

    /**
     * The JVM arguments used to start the Java program.
     * <p>
     * Defaults to {@code []}.
     *
     * @since 4.8.0
     */
    final ListProperty<String> jvmArgs

    DockerJavaApplication(ObjectFactory objectFactory) {
        baseImage = objectFactory.property(String)
        baseImage.set('openjdk:jre-alpine')
        maintainer = objectFactory.property(String)
        maintainer.set(System.getProperty('user.name'))
        ports = objectFactory.listProperty(Integer)
        ports.set([8080])
        tags = objectFactory.listProperty(String)
        jvmArgs = objectFactory.listProperty(String).empty()
    }
}
