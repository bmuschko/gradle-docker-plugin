package com.bmuschko.gradle.docker.internal

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path

class DefaultDockerUrlValueSourceTest extends Specification {

    @TempDir
    Path temporaryFolder

    def "returns DOCKER_HOST value if env is set"() {
        given:
        def dockerHostEnv = 'unix:///var/run/test-docker.sock'

        DefaultDockerUrlValueSource dockerUrlValueSource = Spy(DefaultDockerUrlValueSource.class)
        dockerUrlValueSource.getEnv('DOCKER_HOST') >> dockerHostEnv

        expect:
        dockerUrlValueSource.obtain() == dockerHostEnv
    }

    @RestoreSystemProperties
    @Unroll
    def "returns default fallback docker url for all operation systems: #osName"() {
        given:
        System.setProperty('user.home', temporaryFolder.resolve('home').toAbsolutePath().toString())
        System.setProperty('os.name', osName)

        DefaultDockerUrlValueSource dockerUrlValueSource = Spy(DefaultDockerUrlValueSource.class)
        dockerUrlValueSource.getEnv('DOCKER_HOST') >> null
        dockerUrlValueSource.isFileExists(_) >> false

        expect:
        dockerUrlValueSource.obtain() == 'tcp://127.0.0.1:2375'

        where:
        osName << ['Windows 10', 'Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns npipe:////./pipe/docker_engine if file exists on Windows"() {
        given:
        System.setProperty('os.name', 'Windows 10')

        DefaultDockerUrlValueSource dockerUrlValueSource = Spy(DefaultDockerUrlValueSource.class)
        dockerUrlValueSource.getEnv('DOCKER_HOST') >> null
        dockerUrlValueSource.isFileExists('\\\\.\\pipe\\docker_engine') >> true

        expect:
        dockerUrlValueSource.obtain() == 'npipe:////./pipe/docker_engine'
    }

    @RestoreSystemProperties
    def "returns unix:///var/run/docker.sock url if file exists on #osName"() {
        given:
        System.setProperty('os.name', osName)

        DefaultDockerUrlValueSource dockerUrlValueSource = Spy(DefaultDockerUrlValueSource.class)
        dockerUrlValueSource.getEnv('DOCKER_HOST') >> null
        dockerUrlValueSource.isFileExists('/var/run/docker.sock') >> true

        expect:
        dockerUrlValueSource.obtain() == 'unix:///var/run/docker.sock'

        where:
        osName << ['Mac OS X', 'Linux']
    }

    @RestoreSystemProperties
    def "returns unix://{user.home}/.docker/run/docker.sock url if file exists on #osName"() {
        given:
        System.setProperty('os.name', osName)
        System.setProperty('user.home', '/home/test')

        DefaultDockerUrlValueSource dockerUrlValueSource = Spy(DefaultDockerUrlValueSource.class)
        dockerUrlValueSource.getEnv('DOCKER_HOST') >> null
        dockerUrlValueSource.isFileExists('/var/run/docker.sock') >> false
        dockerUrlValueSource.isFileExists('/home/test/.docker/run/docker.sock') >> true

        expect:
        dockerUrlValueSource.obtain() == "unix:///home/test/.docker/run/docker.sock"

        where:
        osName << ['Mac OS X', 'Linux']
    }
}
