package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import spock.lang.Unroll

import java.lang.reflect.InvocationTargetException

class DockerThreadContextClassLoaderIntegrationTest extends AbstractIntegrationTest {
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

    def "Can create class of type Volume"() {
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

    def "Can create class of type Volumes"() {
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
    def "Can create class of type InternetProtocol with scheme #scheme"() {
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

    def "Can create class of type ExposedPort"() {
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

    def "Can create class of type PortBinding"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createPortBinding('8080:80')
        }

        then:
        noExceptionThrown()
        instance
    }

    def "Can create class of type Ports"() {
        when:
        def instance

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            def portBinding1 = createPortBinding('8080:80')
            def portBinding2 = createPortBinding('9090:90')
            instance = createPorts([portBinding1, portBinding2])
        }

        then:
        noExceptionThrown()
        instance
        instance.ports.size() == 2
    }

    def "Can create class of type Link"() {
        when:
        def instance = null

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createLink('name:alias')
        }

        then:
        noExceptionThrown()
        instance
    }

    def "Can create class of type Links"() {
        when:
        def instance = null

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            def link = createLink('name:alias')
            def link2 = createLink('name2:alias2')
            instance = createLinks([link, link2])
        }

        then:
        noExceptionThrown()
        instance
        instance.links.size() == 2
    }

    def "Can create class of type HostConfig"() {
        when:
        def instance = null

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createHostConfig(["links": []])
        }

        then:
        noExceptionThrown()
        instance
        instance.links.links.size() == 0
    }

    def "Can create class of type Bind"() {
        when:
        def instance = null
        def path = '/my/path'
        def volume = '/my/volume'

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createBind(path, volume)
        }

        then:
        noExceptionThrown()
        instance
        instance.path == path
        instance.volume.path == volume
    }

    def "Can create class of type Binds"() {
        when:
        def instance = null

        def binds = ['/my/path': 'my/volume', '/other/path': '/other/volume']

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createBinds(binds)
        }

        then:
        noExceptionThrown()
        instance
        instance.length == binds.size()
        instance[0].path == '/my/path'
        instance[0].volume.path == 'my/volume'
        instance[1].path == '/other/path'
        instance[1].volume.path == '/other/volume'
    }

    def "Can create class of type AuthConfig"() {
        when:
        def instance = null
        DockerRegistryCredentials credentials = createCredentials()

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createAuthConfig(credentials)
        }

        then:
        noExceptionThrown()
        instance
        instance.serverAddress == DockerRegistryCredentials.DEFAULT_URL
        instance.username == 'username'
        instance.password == 'password'
        instance.email == 'username@gmail.com'
    }

    def "Can create class of type AuthConfigurations"() {
        when:
        def instance = null
        DockerRegistryCredentials credentials1 = createCredentials()
        credentials1.url = 'http://server1.com/'
        DockerRegistryCredentials credentials2 = createCredentials()
        credentials2.url = 'http://server2.com/'

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            def authConfig1 = createAuthConfig(credentials1)
            def authConfig2 = createAuthConfig(credentials2)
            instance = createAuthConfigurations([authConfig1, authConfig2])
        }

        then:
        noExceptionThrown()
        instance
        instance.configs.size() == 2
    }

    def "Can create class of type VolumesFrom"() {
        when:
        def instance = null

        def volumes = ['volume-one', 'volume-two:ro', 'volume-three:rw'] as String[]

        threadContextClassLoader.withClasspath(project.configurations.dockerJava.files, dockerClientConfiguration) {
            instance = createVolumesFrom(volumes)
        }

        then:
        noExceptionThrown()
        instance
        instance.size() == volumes.size()
        instance[0].container == 'volume-one'
        instance[0].accessMode.toString() == 'rw'
        instance[1].container == 'volume-two'
        instance[1].accessMode.toString() == 'ro'
        instance[2].container == 'volume-three'
        instance[2].accessMode.toString() == 'rw'
    }

    private DockerRegistryCredentials createCredentials() {
        DockerRegistryCredentials credentials = new DockerRegistryCredentials()

        credentials.with {
            username = 'username'
            password = 'password'
            email = 'username@gmail.com'
        }

        credentials
    }
}
