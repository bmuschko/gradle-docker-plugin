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
        new DefaultDockerConfigResolver().defaultDockerUrl == dockerHostEnv
    }

    @RestoreSystemProperties
    @Unroll
    def "returns default fallback docker url for all operation systems: #osName"() {
        given:
        SystemConfig.setProperty('user.home', temporaryFolder.resolve('home').toAbsolutePath().toString())
        SystemConfig.setProperty('os.name', osName)

        expect:
        new DefaultDockerConfigResolver().defaultDockerUrl == 'tcp://127.0.0.1:2375'

        where:
        osName << ['Windows 10', 'Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns npipe:////./pipe/docker_engine if file exists on Windows"() {
        given:
        SystemConfig.setProperty('os.name', 'Windows 10')

        expect:
        new DefaultDockerConfigResolver() {
            @Override
            protected boolean isWinPipeDockerEngineExists() {
                return true
            }
        }.getDefaultDockerUrl() == 'npipe:////./pipe/docker_engine'
    }

    @RestoreSystemProperties
    def "returns unix:///var/run/docker.sock url if file exists on #osName"() {
        given:
        SystemConfig.setProperty('os.name', osName)

        expect:
        new DefaultDockerConfigResolver() {
            @Override
            protected boolean isVarRunDockerSockExists() {
                return true
            }
        }.getDefaultDockerUrl() == 'unix:///var/run/docker.sock'

        where:
        osName << ['Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns unix://{user.home}/.docker/run/docker.sock url if file exists on #osName"() {
        given:
        SystemConfig.setProperty('os.name', osName)
        SystemConfig.setProperty('user.home', '/home/test')

        expect:
        new DefaultDockerConfigResolver() {
            @Override
            protected boolean isUserHomeDockerSockExists() {
                return true
            }
        }.getDefaultDockerUrl() == "unix:///home/test/.docker/run/docker.sock"

        where:
        osName << ['Mac OS X', 'Linux']
    }

    def "returns null if DOCKER_CERT_PATH is not set"() {
        expect:
        new DefaultDockerConfigResolver().defaultDockerCert == null
    }

    def "returns null if DOCKER_CERT_PATH set and leads to a not existed file"() {
        given:
        def dockerCertPath = temporaryFolder.resolve('unknown-docker.crt').toAbsolutePath().toString()
        SystemConfig.setEnv('DOCKER_CERT_PATH', dockerCertPath)

        expect:
        new DefaultDockerConfigResolver().defaultDockerCert == null
    }

    def "returns existed file if DOCKER_CERT_PATH set and leads to a correct file"() {
        given:
        def certFile = temporaryFolder.resolve('docker.crt')
        Files.createFile(certFile)

        SystemConfig.setEnv('DOCKER_CERT_PATH', certFile.toAbsolutePath().toString())

        expect:
        new DefaultDockerConfigResolver().defaultDockerCert == certFile.toFile()
    }

}
