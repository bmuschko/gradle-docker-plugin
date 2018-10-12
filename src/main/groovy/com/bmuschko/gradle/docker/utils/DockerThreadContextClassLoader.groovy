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

import com.bmuschko.gradle.docker.DockerExtension
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import groovy.transform.Memoized
import org.gradle.api.Action
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@SuppressWarnings(['FieldTypeRequired', 'UnnecessaryDefInFieldDeclaration'])
class DockerThreadContextClassLoader implements ThreadContextClassLoader {

    private static final Logger LOGGER = Logging.getLogger(DockerThreadContextClassLoader)

    public static final String CORE_PACKAGE = 'com.github.dockerjava.core'
    public static final String MODEL_PACKAGE = 'com.github.dockerjava.api.model'
    public static final String COMMAND_PACKAGE = 'com.github.dockerjava.core.command'
    public static final String ENHANCER_CLASS = 'com.github.dockerjava.shaded.net.sf.cglib.proxy.Enhancer'
    public static final String INVOCATION_CLASS = 'com.github.dockerjava.shaded.net.sf.cglib.proxy.InvocationHandler'

    private static final String TRAILING_WHIESPACE = /\s+$/
    private static final String COLON_CHAR = ':'

    private static final String PARSE_METHOD_NAME = 'parse'
    private static final String ON_NEXT_METHOD_NAME = 'onNext'

    private final DockerExtension dockerExtension

    DockerThreadContextClassLoader(final DockerExtension dockerExtension) {
        this.dockerExtension = dockerExtension
    }

    /**
     * {@inheritDoc}
     */
    void withContext(final DockerClientConfiguration dockerClientConfiguration, final Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure(getDockerClient(dockerClientConfiguration))
    }

    /**
     * Get, and possibly create, DockerClient.
     *
     * @param dockerClientConfiguration Docker client configuration
     * @param classpathFiles set of files containing DockerClient jars
     * @return DockerClient instance
     */
    @Memoized
    private def getDockerClient(DockerClientConfiguration dockerClientConfiguration) {
        loadClasses(dockerExtension.classpath.files, this.class.classLoader)

        String dockerUrl = getDockerHostUrl(dockerClientConfiguration)
        File dockerCertPath = dockerClientConfiguration.certPath?.asFile ?: dockerExtension.certPath.getOrNull()?.asFile
        String apiVersion = dockerClientConfiguration.apiVersion ?: dockerExtension.apiVersion.getOrNull()

        // Create configuration
        Class dockerClientConfigClass = loadClass("${CORE_PACKAGE}.DockerClientConfig")
        Class dockerClientConfigClassImpl = loadClass("${CORE_PACKAGE}.DefaultDockerClientConfig")
        Method dockerClientConfigMethod = dockerClientConfigClassImpl.getMethod('createDefaultConfigBuilder')
        def dockerClientConfigBuilder = dockerClientConfigMethod.invoke(null)
        dockerClientConfigBuilder.withDockerHost(dockerUrl)

        if (dockerCertPath) {
            dockerClientConfigBuilder.withDockerTlsVerify(true)
            dockerClientConfigBuilder.withDockerCertPath(dockerCertPath.canonicalPath)
        } else {
            dockerClientConfigBuilder.withDockerTlsVerify(false)
        }

        if (apiVersion) {
            dockerClientConfigBuilder.withApiVersion(apiVersion)
        }

        def dockerClientConfig = dockerClientConfigBuilder.build()

        // Create client
        Class dockerClientBuilderClass = loadClass("${CORE_PACKAGE}.DockerClientBuilder")
        Method method = dockerClientBuilderClass.getMethod('getInstance', dockerClientConfigClass)
        def dockerClientBuilder = method.invoke(null, dockerClientConfig)
        def dockerClient = dockerClientBuilder.build()

        // register shutdown-hook to close kubernetes client.
        addShutdownHook {
            dockerClient.close()
        }
        dockerClient
    }

    /**
     * Checks if Docker host URL starts with http(s) and if so, converts it to tcp
     * which is accepted by docker-java library.
     *
     * @param dockerClientConfiguration docker client configuration
     * @return Docker host URL as string
     */
    private String getDockerHostUrl(DockerClientConfiguration dockerClientConfiguration) {
        String url = (dockerClientConfiguration.url ?: dockerExtension.url.getOrNull()).toLowerCase()
        url.startsWith('http') ? 'tcp' + url.substring(url.indexOf(COLON_CHAR)) : url
    }

    /**
     * Load set of files into an arbitrary ClassLoader.
     *
     * @param classpathFiles set of files to load
     * @param loader ClassLoader to load files into
     */
    private loadClasses(final Set<File> classpathFiles, final ClassLoader loader) {
        toURLArray(classpathFiles).each { url ->
            loader.addURL(url)
        }
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
        authConfig.registryAddress = registryCredentials.url.get()
        authConfig.username = registryCredentials.username.getOrNull()
        authConfig.password = registryCredentials.password.getOrNull()
        authConfig.email = registryCredentials.email.getOrNull()
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
        Method parseMethod = volumesClass.getMethod(PARSE_METHOD_NAME, String)
        volumes.collect {
            parseMethod.invoke(null, it)
        }.toArray(Array.newInstance(volumesClass, 0))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLink(String link) {
        Class linkClass = loadClass("${MODEL_PACKAGE}.Link")
        Method method = linkClass.getMethod(PARSE_METHOD_NAME, String)
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
        Method method = internetProtocolClass.getMethod(PARSE_METHOD_NAME, String)
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

        List expPorts = []
        exposedPorts.each { it ->
            it.ports.each { p ->
                expPorts << cExposedPort.newInstance(p, protocolClass.invokeMethod(PARSE_METHOD_NAME, it.internetProtocol.toLowerCase()))
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
        Method method = portBindingClass.getMethod(PARSE_METHOD_NAME, String)
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
            Object[] arguments = [portBindings.toArray(portBindingsArray)]
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
        Class bindClass = loadBindClass()
        Method bindParseMethod = bindClass.getMethod(PARSE_METHOD_NAME, String.class)
        bindParseMethod.invoke(null, [path, volume].join(COLON_CHAR))
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
    def createRestartPolicy(String restartPolicy) {
        Class rpClass = loadClass("${MODEL_PACKAGE}.RestartPolicy")
        Method rpParseMethod = rpClass.getMethod(PARSE_METHOD_NAME, String.class)
        rpParseMethod.invoke(null, restartPolicy)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createDevice(final String deviceString) {
        Class deviceClass = loadClass("${MODEL_PACKAGE}.Device")
        try {
            // The parse method is  available in newer Docker java library versions.
            Method method = deviceClass.getMethod(PARSE_METHOD_NAME, String)
            method.invoke(null, deviceString)
        } catch (NoSuchMethodException) {
            // For older Docker java library versions we must parse the device string ourselves.
            Constructor deviceConstructor = deviceClass.getConstructor(String, String, String)
            deviceConstructor.newInstance(*parseDevice(deviceString))
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createBuildImageResultCallback() {
        createPrintStreamProxyCallback(createCallback("${COMMAND_PACKAGE}.BuildImageResultCallback"))
    }

    @Override
    def createBuildImageResultCallback(Action<Object> nextHandler) {
        createOnNextProxyCallback(nextHandler, createCallback("${COMMAND_PACKAGE}.BuildImageResultCallback"))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPushImageResultCallback(Action<Object> nextHandler) {
        def defaultHandler = createCallback("${COMMAND_PACKAGE}.PushImageResultCallback")
        if (nextHandler) {
            return createOnNextProxyCallback(nextHandler, defaultHandler)
        }

        defaultHandler
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPullImageResultCallback(Action<Object> nextHandler) {
        def defaultHandler = createCallback("${COMMAND_PACKAGE}.PullImageResultCallback")
        if (nextHandler) {
            return createOnNextProxyCallback(nextHandler, defaultHandler)
        }

        defaultHandler
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLoggingCallback() {
        Class callbackClass = loadClass("${COMMAND_PACKAGE}.LogContainerResultCallback")
        def delegate = callbackClass.getConstructor().newInstance()

        Class enhancerClass = loadClass(ENHANCER_CLASS)
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(callbackClass)
        enhancer.setCallback([

            invoke: { Object proxy, Method method, Object[] args ->
                try {
                    if (ON_NEXT_METHOD_NAME == method.name && args.length && args[0]) {
                        def frame = args[0]
                        switch (frame.streamType as String) {
                            case 'STDOUT':
                            case 'RAW':
                                LOGGER.quiet(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                                break
                            case 'STDERR':
                                LOGGER.error(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                                break
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error('Exception thrown inside enhancer callback when invoking method createLoggingCallback', e)
                    return
                }

                try {
                    method.invoke(delegate, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }
        ].asType(loadClass(INVOCATION_CLASS)))

        enhancer.create()
    }

    @Override
    def createLoggingCallback(Action<Object> nextHandler) {
        createOnNextProxyCallback(nextHandler, createCallback("${COMMAND_PACKAGE}.LogContainerResultCallback"))
    }
/**
 * {@inheritDoc}
 */
    @Override
    def createLoggingCallback(Writer sink) {
        Class callbackClass = loadClass("${COMMAND_PACKAGE}.LogContainerResultCallback")
        def delegate = callbackClass.getConstructor().newInstance()

        Class enhancerClass = loadClass(ENHANCER_CLASS)
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(callbackClass)
        enhancer.setCallback([

            invoke: { Object proxy, Method method, Object[] args ->
                try {
                    if (ON_NEXT_METHOD_NAME == method.name && args.length && args[0]) {
                        def frame = args[0]
                        switch (frame.streamType as String) {
                            case 'STDOUT':
                            case 'RAW':
                            case 'STDERR':
                                sink.append(new String(frame.payload))
                                sink.flush()
                                break
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error('Exception thrown inside enhancer callback when invoking method createLoggingCallback', e)
                    return
                }

                try {
                    method.invoke(delegate, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }
        ].asType(loadClass(INVOCATION_CLASS)))

        enhancer.create()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createExecCallback(OutputStream out, OutputStream err) {
        Class callbackClass = loadClass("${COMMAND_PACKAGE}.ExecStartResultCallback")
        Constructor constructor = callbackClass.getConstructor(OutputStream, OutputStream)
        constructor.newInstance(out, err)
    }

    @Override
    def createExecCallback(Action<Object> nextHandler) {
        def defaultHandler = createExecCallback(null, null)

        Class enhancerClass = loadClass(ENHANCER_CLASS)
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(defaultHandler.getClass())
        enhancer.setCallback([
            invoke: { Object proxy, Method method, Object[] args ->
                try {
                    if (ON_NEXT_METHOD_NAME == method.name) {
                        nextHandler.execute(args[0])
                    }
                } catch (Exception e) {
                    LOGGER.error('Exception thrown inside enhancer callback when invoking method createExecCallback', e)
                    return
                }

                try {
                    method.invoke(defaultHandler, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }
        ].asType(loadClass(INVOCATION_CLASS)))

        enhancer.create()
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createWaitContainerResultCallback(Action<Object> nextHandler) {
        def defaultHandler = createCallback("${COMMAND_PACKAGE}.WaitContainerResultCallback")
        if (nextHandler) {
            return createOnNextProxyCallback(nextHandler, defaultHandler)
        }

        defaultHandler
    }

    private createOnNextProxyCallback(Action<Object> nextHandler, defaultHandler) {

        Class enhancerClass = loadClass(ENHANCER_CLASS)
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(defaultHandler.getClass())
        enhancer.setCallback([
            invoke: { Object proxy, Method method, Object[] args ->
                try {
                    if (ON_NEXT_METHOD_NAME == method.name) {
                        nextHandler.execute(args[0])
                    }
                } catch (Exception e) {
                    LOGGER.error('Exception thrown inside enhancer callback when invoking method createOnNextProxyCallback', e)
                    return
                }

                try {
                    method.invoke(defaultHandler, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }
        ].asType(loadClass(INVOCATION_CLASS)))

        enhancer.create()
    }

    private createPrintStreamProxyCallback(delegate) {
        Class enhancerClass = loadClass(ENHANCER_CLASS)
        def enhancer = enhancerClass.getConstructor().newInstance()
        enhancer.setSuperclass(delegate.getClass())
        enhancer.setCallback([

            invoke: { Object proxy, Method method, Object[] args ->
                try {
                    if (ON_NEXT_METHOD_NAME == method.name) {
                        def possibleStream = args[0].stream
                        if (possibleStream) {
                            LOGGER.quiet(possibleStream.trim())
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error('Exception thrown inside enhancer callback when invoking method createPrintStreamProxyCallback', e)
                    return
                }

                try {
                    method.invoke(delegate, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }
        ].asType(loadClass(INVOCATION_CLASS)))

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

    private def parseDevice(String deviceString) {
        List<String> tokens = deviceString.tokenize(COLON_CHAR)

        String permissions = 'rwm'
        String source = ''
        String destination = ''

        switch (tokens.size()) {
            case 3:
                def permissionsTokenNo = 2
                if (validDeviceMode(tokens[permissionsTokenNo])) {
                    permissions = tokens[permissionsTokenNo]
                } else {
                    throw new IllegalArgumentException(
                        "Invalid device specification: $deviceString")
                }
            case 2:
                if (validDeviceMode(tokens[1])) {
                    permissions = tokens[1]
                } else {
                    destination = tokens[1]
                }
            case 1:
                source = tokens[0]
                break
            default:
                throw new IllegalArgumentException("Invalid device specification: $deviceString")
        }

        if (!destination) {
            destination = source
        }

        return [permissions, destination, source]
    }

    private boolean validDeviceMode(String deviceMode) {
        Map<String, Boolean> validModes = [r: true, w: true, m: true]

        if (!deviceMode) {
            return false
        }

        for (mode in deviceMode) {
            if (validModes[mode]) {
                validModes[mode] = false
            } else {
                return false // wrong mode
            }
        }

        return true
    }
}
