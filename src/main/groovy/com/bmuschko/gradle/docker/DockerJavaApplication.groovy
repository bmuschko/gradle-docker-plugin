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
     * The tag used for the Docker image.
     * <p>
     * Defaults to {@code <project.group>/<applicationName>:<project.version>}.
     * @deprecated use {@link #tags} - will be removed in 6.0.0
     */
    @Deprecated
    final Property<String> tag

    /**
     * The tag used for the Docker image.
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

    DockerJavaApplication(ObjectFactory objectFactory) {
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
