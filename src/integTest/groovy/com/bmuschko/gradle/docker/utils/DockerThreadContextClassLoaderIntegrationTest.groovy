package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.ProjectBuilderIntegrationTest
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import spock.lang.Unroll

import java.lang.reflect.InvocationTargetException

class DockerThreadContextClassLoaderIntegrationTest extends ProjectBuilderIntegrationTest {
    ThreadContextClassLoader threadContextClassLoader = new DockerThreadContextClassLoader()
    DockerClientConfiguration dockerClientConfiguration = new DockerClientConfiguration(url: 'http://localhost:2375')

    def setup() {
        project.configurations {
            dockerJava
        }

        project.dependencies {
            dockerJava "com.github.docker-java:docker-java:$DockerRemoteApiPlugin.DOCKER_JAVA_DEFAULT_VERSION"
        }
    }

    def "Can create of class of type Volume"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createVolume('/my/path')
        }

        then:
        noExceptionThrown()
        instance
        instance.toString() == '/my/path'
    }

    def "Can create of class of type Volumes"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            def volume1 = createVolume('/my/path')
            def volume2 = createVolume('/my/other/path')
            instance = createVolumes([volume1, volume2])
        }

        then:
        noExceptionThrown()
        instance
        instance.volumes.length == 2
    }

    @Unroll
    def "Can create of class of type InternetProtocol with scheme #scheme"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createInternetProtocol(scheme)
        }

        then:
        noExceptionThrown()
        instance
        instance.toString() == scheme.toLowerCase()

        where:
        scheme << ['TCP', 'UDP']
    }

    def "Throws exception when creating class of type InternetProtocol with unknown scheme"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createInternetProtocol('UNKNOWN')
        }

        then:
        thrown(InvocationTargetException)
    }

    def "Can create of class of type ExposedPort"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createExposedPort('TCP', 80)
        }

        then:
        noExceptionThrown()
        instance
        instance.toString() == '80/tcp'
    }

    def "Can create of class of type ExposedPorts"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            def exposedPort1 = createExposedPort('TCP', 80)
            def exposedPort2 = createExposedPort('UDP', 90)
            instance = createExposedPorts([exposedPort1, exposedPort2])
        }

        then:
        noExceptionThrown()
        instance
        instance.exposedPorts.length == 2
    }
}
