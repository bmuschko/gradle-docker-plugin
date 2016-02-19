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
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import org.gradle.api.logging.Logger

import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class DefaultDockerClientSite implements DockerClientSite {
    public static final String MODEL_PACKAGE = 'com.github.dockerjava.api.model'
    public static final String COMMAND_PACKAGE = 'com.github.dockerjava.core.command'

    private final Map dockerClientConfiguration
    private def dockerClient

    DefaultDockerClientSite(Map dockerClientConfiguration) {
        this.dockerClientConfiguration = dockerClientConfiguration
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def withDockerClient(@DelegatesTo(DockerClientSite) Closure closure) {
        if (!dockerClient) {
            dockerClient = new DockerClientFactory().getInstance(dockerClientConfiguration)
        }

        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure(dockerClient)
    }

    private class DockerClientFactory {
        private Class configClass
        private Class builderClass

        DockerClientFactory() {
            classLoader.with {
                configClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
                builderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
            }
        }

        def getInstance(Map<String, String> configuration) {
            def configBuilder = configClass
                    .getMethod('createDefaultConfigBuilder')
                    .invoke(null)

            configBuilder.with {
                withUri configuration.url
                withDockerCertPath configuration.certPath?.canonicalPath
                withVersion configuration.apiVersion ?: getApiVersion(build())
                createClient build()
            }
        }

        private def createClient(def configuration) {
            def clientBuilder = builderClass
                .getMethod('getInstance', configClass)
                .invoke(null, configuration)
            clientBuilder.build()
        }

        private String getApiVersion(def configuration) {
            def client = createClient(configuration)
            client.versionCmd().exec().apiVersion
        }
    }

    private ClassLoader getClassLoader() {
        URL[] urls = dockerClientConfiguration.classpath*.toURI()*.toURL()
        dockerClient ? dockerClient.class.classLoader : new URLClassLoader(urls, ClassLoader.systemClassLoader.parent)
    }

    private Class loadClass(String className) {
        classLoader.loadClass(className)
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

    private createPrintStreamProxyCallback(Logger logger, delegate) {
        Class enhancerClass = loadClass('net.sf.cglib.proxy.Enhancer')
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(delegate.getClass())
        enhancer.setCallback([

                invoke: {Object proxy, Method method, Object[] args ->
                    if ("onNext" == method.name) {
                        logger.info(args[0].stream)
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
