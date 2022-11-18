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

import com.bmuschko.gradle.docker.internal.MainClassFinder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;

import java.io.File;
import java.io.IOException;

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Java application.
 * <p>
 * This plugin can be configured with the help of {@link DockerJavaApplication}.
 */
public class DockerJavaApplicationPlugin extends DockerConventionJvmApplicationPlugin<DockerJavaApplication> {

    /**
     * The name of extension registered with type {@link DockerJavaApplication}.
     */
    public static final String JAVA_APPLICATION_EXTENSION_NAME = "javaApplication";

    @Override
    protected DockerJavaApplication configureExtension(ObjectFactory objectFactory, DockerExtension dockerExtension) {
        return ((ExtensionAware) dockerExtension).getExtensions().create(JAVA_APPLICATION_EXTENSION_NAME, DockerJavaApplication.class, objectFactory);
    }

    @Override
    protected String findMainClassName(File classesDir) throws IOException {
        return MainClassFinder.findSingleMainClass(classesDir);
    }
}
