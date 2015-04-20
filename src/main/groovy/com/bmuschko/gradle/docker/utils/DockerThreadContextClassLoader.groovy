/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.utils

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Array

class DockerThreadContextClassLoader implements ThreadContextClassLoader {
    /**
     * {@inheritDoc}
     */
    @Override
    void withClasspath(Set<File> classpathFiles, DockerClientConfiguration dockerClientConfiguration, Closure closure) {
        ClassLoader originalClassLoader = getClass().classLoader

        try {
            Thread.currentThread().contextClassLoader = createClassLoader(classpathFiles)
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            closure.delegate = this
            closure(getDockerClient(dockerClientConfiguration))
        }
        finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    /**
     * Creates the classloader with the given classpath files.
     *
     * @param classpathFiles Classpath files
     * @return URL classloader
     */
    private URLClassLoader createClassLoader(Set<File> classpathFiles) {
        new URLClassLoader(toURLArray(classpathFiles), ClassLoader.systemClassLoader.parent)
    }

    /**
     * Creates URL array from a set of files.
     *
     * @param files Files
     * @return URL array
     */
    private URL[] toURLArray(Set<File> files) {
        files.collect { file -> file.toURI().toURL() } as URL[]
    }

    /**
     * Creates DockerClient from ClassLoader.
     *
     * @param dockerClientConfiguration Docker client configuration
     * @return DockerClient instance
     */
    private getDockerClient(DockerClientConfiguration dockerClientConfiguration) {
        // Create configuration
        Class dockerClientConfigClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
        Method dockerClientConfigMethod = dockerClientConfigClass.getMethod('createDefaultConfigBuilder')
        def dockerClientConfigBuilder = dockerClientConfigMethod.invoke(null)
        dockerClientConfigBuilder.withUri(dockerClientConfiguration.url)

        if(dockerClientConfiguration.certPath) {
            dockerClientConfigBuilder.withDockerCertPath(dockerClientConfiguration.certPath.canonicalPath)
        }

        def dockerClientConfig = dockerClientConfigBuilder.build()

        // Create client
        Class dockerClientBuilderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
        Method method = dockerClientBuilderClass.getMethod('getInstance', dockerClientConfigClass)
        def dockerClientBuilder = method.invoke(null, dockerClientConfig)
        dockerClientBuilder.build()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Class loadClass(String className) {
        Thread.currentThread().contextClassLoader.loadClass(className)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createAuthConfig(DockerRegistryCredentials registryCredentials) {
        Class authConfigClass = loadClass('com.github.dockerjava.api.model.AuthConfig')
        def authConfig = authConfigClass.newInstance()
        authConfig.serverAddress = registryCredentials.url
        authConfig.username = registryCredentials.username
        authConfig.password = registryCredentials.password
        authConfig.email = registryCredentials.email
        authConfig
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createVolume(String path) {
        Class volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
        Constructor constructor = volumeClass.getConstructor(String)
        constructor.newInstance(path)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createVolumes(List<Object> volumes) {
        Class volumesClass = loadClass('com.github.dockerjava.api.model.Volumes')
        Constructor constructor = volumesClass.getConstructor(List)
        constructor.newInstance(volumes)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLink(String link) {
        Class linkClass = loadClass('com.github.dockerjava.api.model.Link')
        Method method = linkClass.getMethod("parse", String)
        method.invoke(null, link)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLinks(List<Object> links) {
        Class linksClass = loadClass('com.github.dockerjava.api.model.Links')
        Constructor constructor = linksClass.getConstructor(List.class)
        constructor.newInstance(links)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createHostConfig(Map<String, String> hostConfigProperties) {
        Class hostConfigClass = loadClass('com.github.dockerjava.api.model.HostConfig')
        Constructor constructor = hostConfigClass.getConstructor()
        def hostConfig = constructor.newInstance()
        hostConfigProperties.each { key, value ->
            hostConfig."${key}" = value
        }
        hostConfig
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createExposedPort(String scheme, Integer port) {
        Class exposedPortClass = loadClass('com.github.dockerjava.api.model.ExposedPort')
        Constructor constructor = exposedPortClass.getConstructor(Integer.TYPE, loadInternetProtocolClass())
        constructor.newInstance(port, createInternetProtocol(scheme))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createInternetProtocol(String scheme) {
        Class internetProtocolClass = loadInternetProtocolClass()
        Method method = internetProtocolClass.getMethod('parse', String)
        method.invoke(null, scheme)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createExposedPorts(List<Object> exposedPorts) {
        Class exposedPortsClass = loadClass('com.github.dockerjava.api.model.ExposedPorts')
        Constructor constructor = exposedPortsClass.getConstructor(List)
        constructor.newInstance(exposedPorts)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPortBinding(String portBinding) {
        Class portBindingClass = loadClass('com.github.dockerjava.api.model.PortBinding')
        Method method = portBindingClass.getMethod('parse', String)
        method.invoke(null, portBinding)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPorts(List<Object> portBindings) {
        Class portsClass = loadClass('com.github.dockerjava.api.model.Ports')
        Constructor constructor = portsClass.getConstructor()
        def ports = constructor.newInstance()
        if (!portBindings.isEmpty()) {
            Class portBindingClass = portBindings[0].getClass()
            def portBindingsArray = Array.newInstance(portBindingClass, portBindings.size())
            def portBindingsArrayClass = portBindingsArray.getClass()
            Object[] arguments = [ portBindings.toArray(portBindingsArray) ]
            Method method = portsClass.getMethod('add', portBindingsArrayClass)
            method.invoke(ports, arguments)
        }
        ports
    }

    def createBind(String path, String volume) {

        Class volumeClass = loadClass('com.github.dockerjava.api.model.Volume')
        Constructor volumeConstructor = volumeClass.getConstructor(String)
        def volumeInstance = volumeConstructor.newInstance(path)

        Class bindClass = loadClass('com.github.dockerjava.api.model.Bind')
        Constructor bindConstructor = bindClass.getConstructor(String, volumeClass)
        bindConstructor.newInstance(path, volumeInstance)
    }

    def createBinds(Map<String, String> binds) {

        def bindList = binds.collect { createBind(it.key, it.value) }
        Class bindClass = loadClass('com.github.dockerjava.api.model.Bind')
        bindList.toArray(Array.newInstance(bindClass, bindList.size()))
    }

    private Class loadInternetProtocolClass() {
        loadClass('com.github.dockerjava.api.model.InternetProtocol')
    }
}
