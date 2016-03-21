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
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class DockerThreadContextClassLoader implements ThreadContextClassLoader {
    public static final String MODEL_PACKAGE = 'com.github.dockerjava.api.model'
    public static final String COMMAND_PACKAGE = 'com.github.dockerjava.core.command'

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
        Class authConfigClass = loadClass("${MODEL_PACKAGE}.AuthConfig")
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
    def createAuthConfigurations(List<Object> authConfigs) {
        Class authConfigurationsClass = loadClass("${MODEL_PACKAGE}.AuthConfigurations")
        def authConfigurations = authConfigurationsClass.newInstance()

        authConfigs.each { authConfig ->
            authConfigurations.addConfig(authConfig)
        }

        authConfigurations
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createVolume(String path) {
        Class volumeClass = loadClass("${MODEL_PACKAGE}.Volume")
        Constructor constructor = volumeClass.getConstructor(String)
        constructor.newInstance(path)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createVolumes(List<Object> volumes) {
        Class volumesClass = loadClass("${MODEL_PACKAGE}.Volumes")
        Constructor constructor = volumesClass.getConstructor(List)
        constructor.newInstance(volumes)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createVolumesFrom(String[] volumes) {
        Class volumesClass = loadClass("${MODEL_PACKAGE}.VolumesFrom")
        Method parseMethod = volumesClass.getMethod('parse', String)
        volumes.collect { parseMethod.invoke(null, it) }.toArray(Array.newInstance(volumesClass, 0))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLink(String link) {
        Class linkClass = loadClass("${MODEL_PACKAGE}.Link")
        Method method = linkClass.getMethod("parse", String)
        method.invoke(null, link)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLinks(List<Object> links) {
        Class linksClass = loadClass("${MODEL_PACKAGE}.Links")
        Constructor constructor = linksClass.getConstructor(List.class)
        constructor.newInstance(links)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createHostConfig(Map<String, String> hostConfigProperties) {
        Class hostConfigClass = loadClass("${MODEL_PACKAGE}.HostConfig")
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
        Class exposedPortClass = loadClass("${MODEL_PACKAGE}.ExposedPort")
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
        Class exposedPortsClass = loadClass("${MODEL_PACKAGE}.ExposedPorts")
        Constructor constructor = exposedPortsClass.getConstructor(List)
        constructor.newInstance(exposedPorts)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createExposedPortsArray(List<DockerCreateContainer.ExposedPort> exposedPorts) {
        Class exposedPortClass = loadClass("${MODEL_PACKAGE}.ExposedPort")

        def protocolClass = loadInternetProtocolClass()
        Constructor cExposedPort = exposedPortClass.getConstructor(Integer.TYPE, protocolClass)

        List expPorts = new ArrayList<>();
        exposedPorts.each { it ->
            it.ports.each { p ->
                expPorts << cExposedPort.newInstance(p, protocolClass.invokeMethod("parse", it.internetProtocol.toLowerCase()))
            }
        }

        def res = Array.newInstance(exposedPortClass, expPorts.size())
        for (int i = 0; i < expPorts.size(); ++i) {
            Object o = expPorts.get(i)
            res[i] = exposedPortClass.cast(o)
        }

        res
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPortBinding(String portBinding) {
        Class portBindingClass = loadClass("${MODEL_PACKAGE}.PortBinding")
        Method method = portBindingClass.getMethod('parse', String)
        method.invoke(null, portBinding)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPorts(List<Object> portBindings) {
        Class portsClass = loadClass("${MODEL_PACKAGE}.Ports")
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

    /**
     * {@inheritDoc}
     */
    @Override
    def createBind(String path, String volume) {
        Class volumeClass = loadClass("${MODEL_PACKAGE}.Volume")
        Constructor volumeConstructor = volumeClass.getConstructor(String)
        def volumeInstance = volumeConstructor.newInstance(volume)

        Class bindClass = loadBindClass()
        Constructor bindConstructor = bindClass.getConstructor(String, volumeClass)
        bindConstructor.newInstance(path, volumeInstance)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createBinds(Map<String, String> binds) {
        def bindList = binds.collect { createBind(it.key, it.value) }
        Class bindClass = loadBindClass()
        bindList.toArray(Array.newInstance(bindClass, bindList.size()))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLogConfig(String type, Map<String, String> parameters) {
        Class logConfigClass = loadClass("${MODEL_PACKAGE}.LogConfig")
        Class logTypeClass = loadClass("${MODEL_PACKAGE}.LogConfig\$LoggingType")
        def logTypeEnum = logTypeClass.values().find { it.type == type }
        Constructor logConfigConstructor = logConfigClass.getConstructor(logTypeClass, Map)
        logConfigConstructor.newInstance(logTypeEnum, parameters)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createBuildImageResultCallback(Logger logger) {
        createPrintStreamProxyCallback(logger, createCallback("${COMMAND_PACKAGE}.BuildImageResultCallback"))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPushImageResultCallback() {
        createCallback("${COMMAND_PACKAGE}.PushImageResultCallback")
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPullImageResultCallback() {
        createCallback("${COMMAND_PACKAGE}.PullImageResultCallback")
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLoggingCallback(Logger logger) {
        Class callbackClass = loadClass("${COMMAND_PACKAGE}.LogContainerResultCallback")
        def delegate = callbackClass.getConstructor().newInstance()

        Class enhancerClass = loadClass('net.sf.cglib.proxy.Enhancer')
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(callbackClass)
        enhancer.setCallback([

            invoke: {Object proxy, java.lang.reflect.Method method, Object[] args ->
                if ("onNext" == method.name && args.length && args[0]) {
                  def frame = args[0]
                  switch (frame.streamType as String) {
                    case "STDOUT":
                    case "RAW":
                        logger.quiet(new String(frame.payload))
                        break
                    case "STDERR":
                        logger.error(new String(frame.payload))
                        break
                  }
                }
                method.invoke(delegate, args)
            }

        ].asType(loadClass('net.sf.cglib.proxy.InvocationHandler')))

        enhancer.create()
    }

    private createPrintStreamProxyCallback(Logger logger, delegate) {
        Class enhancerClass = loadClass('net.sf.cglib.proxy.Enhancer')
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(delegate.getClass())
        enhancer.setCallback([

            invoke: {Object proxy, Method method, Object[] args ->
                if ("onNext" == method.name) {
                    def possibleStream = args[0].stream
                    if (possibleStream)
                        logger.quiet(possibleStream)
                }
                method.invoke(delegate, args)
            }

        ].asType(loadClass('net.sf.cglib.proxy.InvocationHandler')))

        enhancer.create()
    }

    private Object createCallback(String className) {
        Class callbackClass = loadClass(className)
        Constructor constructor = callbackClass.getConstructor()
        constructor.newInstance()
    }

    private Class loadInternetProtocolClass() {
        loadClass("${MODEL_PACKAGE}.InternetProtocol")
    }

    private Class loadBindClass() {
        loadClass("${MODEL_PACKAGE}.Bind")
    }
}
