package com.bmuschko.gradle.docker.internal

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

class DefaultDockerConfigResolverTest extends Specification {

    @TempDir
    Path temporaryFolder

    def "returns DOCKER_HOST value if env is set"() {
        given:
        def dockerHostEnv = 'unix:///var/run/test-docker.sock'

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_HOST') >> dockerHostEnv

        expect:
        dockerConfigResolver.defaultDockerUrl == dockerHostEnv
    }

    @RestoreSystemProperties
    @Unroll
    def "returns default fallback docker url for all operation systems: #osName"() {
        given:
        System.setProperty('user.home', temporaryFolder.resolve('home').toAbsolutePath().toString())
        System.setProperty('os.name', osName)

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_HOST') >> null
        dockerConfigResolver.isFileExists(_) >> null

        expect:
        dockerConfigResolver.defaultDockerUrl == 'tcp://127.0.0.1:2375'

        where:
        osName << ['Windows 10', 'Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns npipe:////./pipe/docker_engine if file exists on Windows"() {
        given:
        System.setProperty('os.name', 'Windows 10')

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_HOST') >> null
        dockerConfigResolver.isFileExists('\\\\.\\pipe\\docker_engine') >> true

        expect:
        dockerConfigResolver.getDefaultDockerUrl() == 'npipe:////./pipe/docker_engine'
    }

    @RestoreSystemProperties
    def "returns unix:///var/run/docker.sock url if file exists on #osName"() {
        given:
        System.setProperty('os.name', osName)

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_HOST') >> null
        dockerConfigResolver.isFileExists('/var/run/docker.sock') >> true

        expect:
        dockerConfigResolver.getDefaultDockerUrl() == 'unix:///var/run/docker.sock'

        where:
        osName << ['Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns unix://{user.home}/.docker/run/docker.sock url if file exists on #osName"() {
        given:
        System.setProperty('os.name', osName)
        System.setProperty('user.home', '/home/test')

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_HOST') >> null
        dockerConfigResolver.isFileExists('/home/test/.docker/run/docker.sock') >> true

        expect:
        dockerConfigResolver.getDefaultDockerUrl() == "unix:///home/test/.docker/run/docker.sock"

        where:
        osName << ['Mac OS X', 'Linux']
    }

    def "returns null if DOCKER_CERT_PATH is not set"() {
        given:
        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_CERT_PATH') >> null

        expect:
        dockerConfigResolver.defaultDockerCert == null
    }

    def "returns null if DOCKER_CERT_PATH set and leads to a not existed file"() {
        given:
        def dockerCertPath = temporaryFolder.resolve('unknown-docker.crt').toAbsolutePath().toString()

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_CERT_PATH') >> dockerCertPath

        expect:
        dockerConfigResolver.defaultDockerCert == null
    }

    def "returns existed file if DOCKER_CERT_PATH set and leads to a correct file"() {
        given:
        def certFile = temporaryFolder.resolve('docker.crt')
        Files.createFile(certFile)

        DefaultDockerConfigResolver dockerConfigResolver = Spy(DefaultDockerConfigResolver.class)
        dockerConfigResolver.getEnv('DOCKER_CERT_PATH') >> certFile.toAbsolutePath().toString()

        expect:
        dockerConfigResolver.defaultDockerCert == certFile.toFile()
    }

}
