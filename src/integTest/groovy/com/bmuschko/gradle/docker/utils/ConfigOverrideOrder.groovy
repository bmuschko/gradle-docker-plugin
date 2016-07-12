package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import org.gradle.api.Project
import org.junit.contrib.java.lang.system.EnvironmentVariables

import static com.bmuschko.gradle.docker.utils.DockerThreadContextClassLoader.*

enum ConfigOverrideOrder {
    DEFAULT {
        @Override
        String dockerUrlValue() { "unix:///var/run/docker.sock" }

        @Override
        String dockerCertPathValue() { null }

        @Override
        String dockerApiVersionValue() { "0.0" }

        @Override
        void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars) {
            dockerClientConfiguration.url = null
            dockerClientConfiguration.certPath = null
            dockerClientConfiguration.apiVersion = null
        }
    }, EXTENSION_PROP{
        @Override
        String dockerUrlValue() { "tcp://extension.host" }

        @Override
        String dockerCertPathValue() { "extension.path" }

        @Override
        String dockerApiVersionValue() { "111.4" }

        @Override
        void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars) {
            project.docker {
                url = dockerUrlValue()
                certPath = newFolder(project)
                apiVersion = dockerApiVersionValue()
            }
        }
    }, TASK_PROP{
        @Override
        String dockerUrlValue() { "tcp://task.host" }

        @Override
        String dockerCertPathValue() { "task.path"}

        @Override
        String dockerApiVersionValue() { "111.5" }

        @Override
        void apply(Project project, DockerClientConfiguration taskConfig, EnvironmentVariables envVars) {
            taskConfig.url = dockerUrlValue()
            taskConfig.certPath = newFolder(project)
            taskConfig.apiVersion = dockerApiVersionValue()
        }
    }, ENV_PROP {
        @Override
        String dockerUrlValue() { "tcp://env.property.host" }

        @Override
        String dockerCertPathValue() { "env.property.path" }

        @Override
        String dockerApiVersionValue() { "111.1" }

        @Override
        void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars) {
            envVars.set(DOCKER_HOST_PROPERTY_NAME, dockerUrlValue())
            envVars.set(DOCKER_CERT_PATH_PROPERTY_NAME, newFolder(project).absolutePath)
            envVars.set(DOCKER_API_VERSION_PROPERTY_NAME, dockerApiVersionValue())
        }
    }, SYSTEM_PROP {
        @Override
        String dockerUrlValue() { "tcp://system.property.host" }

        @Override
        String dockerCertPathValue() { "system.property.path" }

        @Override
        String dockerApiVersionValue() { "111.2" }

        @Override
        void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars) {
            System.setProperty(DOCKER_HOST_PROPERTY_NAME, dockerUrlValue())
            System.setProperty(DOCKER_CERT_PATH_PROPERTY_NAME, newFolder(project).absolutePath)
            System.setProperty(DOCKER_API_VERSION_PROPERTY_NAME, dockerApiVersionValue())
        }
    }, PROJECT_PROP {
        @Override
        String dockerUrlValue() { "tcp://project.property.host" }

        @Override
        String dockerCertPathValue() { "project.property.path" }

        @Override
        String dockerApiVersionValue() { "111.3" }

        @Override
        void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars) {
            project.ext[DOCKER_HOST_PROPERTY_NAME] = dockerUrlValue()
            project.ext[DOCKER_CERT_PATH_PROPERTY_NAME] = newFolder(project).absolutePath
            project.ext[DOCKER_API_VERSION_PROPERTY_NAME] = dockerApiVersionValue()
        }
    }

    File newFolder(Project project) { new File(project.projectDir, dockerCertPathValue()) }

    abstract String dockerUrlValue()

    abstract String dockerCertPathValue()

    abstract String dockerApiVersionValue()

    abstract void apply(Project project, DockerClientConfiguration dockerClientConfiguration, EnvironmentVariables envVars)
}