package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Unroll

class DockerThreadContextClassLoaderConfigOverrideIntegrationTest extends AbstractIntegrationTest {

    @Rule
    EnvironmentVariables envVars = new EnvironmentVariables()

    @Rule
    RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()

    DockerClientConfiguration dockerClientConfiguration = new DockerClientConfiguration()

    Project project

    ThreadContextClassLoader threadContextClassLoader

    DockerExtension dockerExtension

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.repositories {
            jcenter()
        }
        project.apply(plugin: DockerRemoteApiPlugin)

        dockerExtension = project.extensions.findByType(DockerExtension)
        threadContextClassLoader = new DockerThreadContextClassLoader(project, dockerExtension)
    }

    private void createFakeCetPathFolders() {
        ConfigOverrideOrder.values()
            .collect { it.dockerCertPathValue() }
            .findAll { it != null }
            .each { temporaryFolder.newFolder(it)
        }
    }

    @Unroll
    def "when override by #config"() {
        given: "create fake folders for cert paths"
        createFakeCetPathFolders()
        and: ""
        def actual

        ConfigOverrideOrder.values().takeWhile { it <= config }
            .each { it.apply(project, dockerClientConfiguration, envVars) }

        when:
        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) { dockerClient ->
            actual = dockerClient.dockerClientConfig
        }

        then: "docker url is properly overridden"
        actual.dockerHost.toString() == config.dockerUrlValue()
        and: "docker cert path is properly overridden"
        actual.sslConfig == null || actual.sslConfig.dockerCertPath.endsWith(config.dockerCertPathValue())
        and: "docker api version is properly overridden"
        actual.apiVersion.version == config.dockerApiVersionValue()

        where:
        config << ConfigOverrideOrder.values()
    }
}
