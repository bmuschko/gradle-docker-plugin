package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.AbstractIntegrationTest
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.DockerRemoteApiPlugin
import spock.lang.Unroll

import java.lang.reflect.InvocationTargetException

class DefaultDockerClientSiteIntegrationTest extends AbstractIntegrationTest {
    DockerClientSite testSubject

    def setup() {
        project.configurations {
            dockerJava
        }

        project.dependencies {
            dockerJava "com.github.docker-java:docker-java:$DockerRemoteApiPlugin.DOCKER_JAVA_DEFAULT_VERSION"
            dockerJava "org.slf4j:slf4j-simple:1.7.5"
        }

        testSubject = new DefaultDockerClientSite(classpath: project.configurations.dockerJava.files, url: 'http://localhost:2375', apiVersion: '1.22')
    }

    def "Can create class of type Volume"() {
        when:
        def instance = testSubject.withDockerClient {
            createVolume('/my/path')
        }

        then:
        noExceptionThrown()
        instance
        instance.toString() == '/my/path'
    }

    def "Can create class of type Volumes"() {
        when:
        def instance = testSubject.withDockerClient {
            def volume1 = createVolume('/my/path')
            def volume2 = createVolume('/my/other/path')
            createVolumes([volume1, volume2])
        }

        then:
        noExceptionThrown()
        instance
        instance.volumes.length == 2
    }

    @Unroll
    def "Can create class of type InternetProtocol with scheme #scheme"() {
        when:
        def instance = testSubject.withDockerClient {
            createInternetProtocol(scheme)
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
        testSubject.withDockerClient {
            createInternetProtocol('UNKNOWN')
        }

        then:
        thrown(InvocationTargetException)
    }

    def "Can create class of type ExposedPort"() {
        when:
        def instance = testSubject.withDockerClient {
            createExposedPort('TCP', 80)
        }

        then:
        noExceptionThrown()
        instance
        instance.toString() == '80/tcp'
    }

    def "Can create of class of type ExposedPorts"() {
        when:
        def instance = testSubject.withDockerClient {
            def exposedPort1 = createExposedPort('TCP', 80)
            def exposedPort2 = createExposedPort('UDP', 90)
            createExposedPorts([exposedPort1, exposedPort2])
        }

        then:
        noExceptionThrown()
        instance
        instance.exposedPorts.length == 2
    }

    def "Can create class of type PortBinding"() {
        when:
        def instance = testSubject.withDockerClient {
            createPortBinding('8080:80')
        }

        then:
        noExceptionThrown()
        instance
    }

    def "Can create class of type Ports"() {
        when:
        def instance = testSubject.withDockerClient {
            def portBinding1 = createPortBinding('8080:80')
            def portBinding2 = createPortBinding('9090:90')
            createPorts([portBinding1, portBinding2])
        }

        then:
        noExceptionThrown()
        instance
        instance.ports.size() == 2
    }

    def "Can create class of type Link"() {
        when:
        def instance = testSubject.withDockerClient {
            createLink('name:alias')
        }

        then:
        noExceptionThrown()
        instance
    }

    def "Can create class of type Links"() {
        when:
        def instance = testSubject.withDockerClient {
            def link = createLink('name:alias')
            def link2 = createLink('name2:alias2')
            createLinks([link, link2])
        }

        then:
        noExceptionThrown()
        instance
        instance.links.size() == 2
    }

    def "Can create class of type HostConfig"() {
        when:
        def instance = testSubject.withDockerClient {
            createHostConfig(["links": []])
        }

        then:
        noExceptionThrown()
        instance
        instance.links.links.size() == 0
    }

    def "Can create class of type Bind"() {
        given:
        def path = '/my/path'
        def volume = '/my/volume'
        when:
        def instance = testSubject.withDockerClient {
            createBind(path, volume)
        }

        then:
        noExceptionThrown()
        instance
        instance.path == path
        instance.volume.path == volume
    }

    def "Can create class of type Binds"() {
        given:
        def binds = ['/my/path': 'my/volume', '/other/path': '/other/volume']
        when:
        def instance = testSubject.withDockerClient {
            createBinds(binds)
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

    def "Can create class of type LogConfig"() {
        given:
        def type = "json-file"
        def parameters = [:]
        when:
        def instance = testSubject.withDockerClient {
            createLogConfig(type, parameters)
        }

        then:
        noExceptionThrown()
        instance
        instance.type.getType() == type
        instance.config.size() == parameters.size()
    }

    def "Can create class of type AuthConfig"() {
        given:
        DockerRegistryCredentials credentials = createCredentials()
        when:
        def instance = testSubject.withDockerClient {
            createAuthConfig(credentials)
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
        given:
        DockerRegistryCredentials credentials1 = createCredentials('http://server1.com/')
        DockerRegistryCredentials credentials2 = createCredentials('http://server2.com/')
        when:
        def instance = testSubject.withDockerClient {
            def authConfig1 = createAuthConfig(credentials1)
            def authConfig2 = createAuthConfig(credentials2)
            createAuthConfigurations([authConfig1, authConfig2])
        }

        then:
        noExceptionThrown()
        instance
        instance.configs.size() == 2
    }

    def "Can create class of type VolumesFrom"() {
        given:
        def volumes = ['volume-one', 'volume-two:ro', 'volume-three:rw'] as String[]
        when:
        def instance = testSubject.withDockerClient {
            createVolumesFrom(volumes)
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

    private static DockerRegistryCredentials createCredentials(String url) {
        createCredentials().with {
            it.url = url
            it
        }
    }

    private static DockerRegistryCredentials createCredentials() {
        new DockerRegistryCredentials().with {
            username = 'username'
            password = 'password'
            email = 'username@gmail.com'
            it
        }
    }
}
