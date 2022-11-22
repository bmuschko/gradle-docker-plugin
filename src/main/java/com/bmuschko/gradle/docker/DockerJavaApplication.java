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
package com.bmuschko.gradle.docker;

import org.gradle.api.model.ObjectFactory;

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
public class DockerJavaApplication extends DockerConventionJvmApplicationExtension {

    public DockerJavaApplication(ObjectFactory objectFactory) {
        super(objectFactory);
    }
}
