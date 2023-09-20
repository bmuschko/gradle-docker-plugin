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
