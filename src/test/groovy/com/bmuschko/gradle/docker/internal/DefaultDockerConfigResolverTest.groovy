package com.bmuschko.gradle.docker.internal

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

class DefaultDockerConfigResolverTest extends Specification {

    private static String fallbackDockerUrl = 'tcp://127.0.0.1:2375'

    @TempDir
    Path temporaryFolder

    void setup() throws IOException {
        SystemConfig.clear()

        // override system envs with null values, to avoid flaky side effects
        SystemConfig.setEnv('DOCKER_HOST', null)
        SystemConfig.setEnv('DOCKER_CERT_PATH', null)
    }

    def "returns DOCKER_HOST value if env is set"() {
        given:
        def dockerHostEnv = 'unix:///var/run/test-docker.sock'
        SystemConfig.setEnv('DOCKER_HOST', dockerHostEnv)

        expect:
        DefaultDockerConfigResolver.defaultDockerUrl == dockerHostEnv
    }

    @Unroll
    def "returns default fallback docker url for all operation systems: #osName"() {
        given:
        SystemConfig.setProperty('user.home', temporaryFolder.resolve('home').toAbsolutePath().toString())
        SystemConfig.setProperty('os.name', osName)

        expect:
        DefaultDockerConfigResolver.defaultDockerUrl == fallbackDockerUrl

        where:
        osName << ['Windows 10', 'Mac OS X', 'Linux']
    }

    def "returns npipe:////./pipe/docker_engine if file exists on Windows"() {
        given:
        SystemConfig.setProperty('os.name', 'Windows 10')

        expect:
        DefaultDockerConfigResolver.getDefaultDockerUrl(
                { true },
                { false },
                { false }
        ) == 'npipe:////./pipe/docker_engine'
    }

    def "returns unix:///var/run/docker.sock url if file exists on #osName"() {
        given:
        SystemConfig.setProperty('os.name', osName)

        expect:
        DefaultDockerConfigResolver.getDefaultDockerUrl(
                { false },
                { true },
                { false }
        ) == 'unix:///var/run/docker.sock'

        where:
        osName << ['Mac OS X', 'Linux']
    }

    def "returns unix://{user.home}/.docker/run/docker.sock url if file exists on #osName"() {
        given:
        SystemConfig.setProperty('os.name', osName)
        SystemConfig.setProperty('user.home', '/home/test')

        expect:
        DefaultDockerConfigResolver.getDefaultDockerUrl(
                { false },
                { false },
                { true }
        ) == "unix:///home/test/.docker/run/docker.sock"

        where:
        osName << ['Mac OS X', 'Linux']
    }

    def "returns null if DOCKER_CERT_PATH is not set"() {
        expect:
        DefaultDockerConfigResolver.defaultDockerCert == null
    }

    def "returns null if DOCKER_CERT_PATH set and leads to a not existed file"() {
        given:
        def dockerCertPath = temporaryFolder.resolve('unknown-docker.crt').toAbsolutePath().toString()
        SystemConfig.setEnv('DOCKER_CERT_PATH', dockerCertPath)

        expect:
        DefaultDockerConfigResolver.defaultDockerCert == null
    }

    def "returns existed file if DOCKER_CERT_PATH set and leads to a correct file"() {
        given:
        def certFile = temporaryFolder.resolve('docker.crt')
        Files.createFile(certFile)

        SystemConfig.setEnv('DOCKER_CERT_PATH', certFile.toAbsolutePath().toString())

        expect:
        DefaultDockerConfigResolver.defaultDockerCert == certFile.toFile()
    }

}
