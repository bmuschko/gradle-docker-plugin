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

import org.xeustechnologies.jcl.JarClassLoader
import org.xeustechnologies.jcl.JclObjectFactory
import org.xeustechnologies.jcl.JclUtils

import groovy.transform.Synchronized
import org.gradle.api.logging.Logger
import org.gradle.api.GradleException

import java.lang.reflect.Array
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default
import net.bytebuddy.NamingStrategy.PrefixingRandom
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.implementation.InvocationHandlerAdapter

class DockerThreadContextClassLoader implements ThreadContextClassLoader {
    public static final String MODEL_PACKAGE = 'com.github.dockerjava.api.model'
    public static final String COMMAND_PACKAGE = 'com.github.dockerjava.core.command'
    private static final TRAILING_WHIESPACE = /\s+$/

    private final DockerExtension dockerExtension
    private def dockerClient // lazily created `docker-java-client`

    private JarClassLoader dockerClientClassLoader // lazily created ClassLoader
    private JclObjectFactory dockerClientObjectFactory // lazily created object factory from ClassLoader


    public DockerThreadContextClassLoader(final DockerExtension dockerExtension) {
        this.dockerExtension = dockerExtension
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void withClasspath(Set<File> classpath, DockerClientConfiguration dockerClientConfiguration, Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure(getDockerClient(dockerClientConfiguration, classpath))
    }

    /**
     * Gets, and possibly creates, a DockerClient built from our custom class-loader.
     *
     * @param dockerClientConfiguration Docker client configuration.
     * @param classpathFiles classpathFiles to possibly initialize custom class-loader with.
     * @return DockerClient instance
     */
    @Synchronized
    private getDockerClient(final DockerClientConfiguration dockerClientConfiguration, final Set<File> classpathFiles) {

        if(!dockerClient) {

            // init custom class-loader
            initializeDockerClassLoader(classpathFiles ?: dockerExtension.classpath?.files)

            String dockerUrl = getDockerHostUrl(dockerClientConfiguration)
            File certPath = dockerClientConfiguration.certPath ?: dockerExtension.certPath
            String apiVersion = dockerClientConfiguration.apiVersion ?: dockerExtension.apiVersion

            // Create configuration
            Class dockerClientConfigClass = loadClass('com.github.dockerjava.core.DockerClientConfig')
            Class dockerClientConfigClassImpl = loadClass('com.github.dockerjava.core.DefaultDockerClientConfig')
            Method dockerClientConfigMethod = dockerClientConfigClassImpl.getMethod('createDefaultConfigBuilder')
            def dockerClientConfigBuilder = dockerClientConfigMethod.invoke(null)
            dockerClientConfigBuilder.withDockerHost(dockerUrl)

            if (certPath) {
                dockerClientConfigBuilder.withDockerTlsVerify(true)
                dockerClientConfigBuilder.withDockerCertPath(certPath.canonicalPath)
            } else {
                dockerClientConfigBuilder.withDockerTlsVerify(false)
            }

            if (apiVersion) {
                dockerClientConfigBuilder.withApiVersion(apiVersion)
            }

            def dockerClientConfig = dockerClientConfigBuilder.build()

            // Create client
            Class dockerClientBuilderClass = loadClass('com.github.dockerjava.core.DockerClientBuilder')
            Method method = dockerClientBuilderClass.getMethod('getInstance', dockerClientConfigClass)
            def dockerClientBuilder = method.invoke(null, dockerClientConfig)
            dockerClient = dockerClientBuilder.build()

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    dockerClient.close();
                }
            });
        }

        dockerClient
    }

    /**
     * Create our custom class-loader loaded with passed in jar files/classes.
     */
    private initializeDockerClassLoader(final Set<File> classpathFiles) {
        if (!dockerClientClassLoader) {

            // 1.) create custom JCL class-loader to store our
            // `docker-java` classpath.
            dockerClientClassLoader = new JarClassLoader()

            // 2.) load all requires libraries for `docker-java` into
            // our custom class-loader for isolated execution.
            dockerClientClassLoader.addAll(classpathFiles.
                collect { it.toURI().toURL() } as URL[])

            // 3.) OPTIONAL object factory to use for creating objects from
            // our custom class-loader. As it can be a bit finicky to use
            // it's not required so long as calling/creating code loads
            // classes from our custom class-loader and not some other source.
            dockerClientObjectFactory = JclObjectFactory.getInstance()
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Class loadClass(final String className) {
        dockerClientClassLoader.loadClass(className)
    }

    /**
     * Create an object with a no-arg constructor from our custom class-loader.
     */
    private Object createObject(final String className) {
        dockerClientObjectFactory.create(dockerClientClassLoader, className)
    }

    /**
     * Checks if Docker host URL starts with http(s) and if so, converts it to tcp
     * which is accepted by docker-java library.
     *
     * @param dockerClientConfiguration docker client configuration
     * @return Docker host URL as string
     */
    private String getDockerHostUrl(DockerClientConfiguration dockerClientConfiguration) {
        String url = (dockerClientConfiguration.url ?: dockerExtension.url).toLowerCase()
        url.startsWith("http") ? "tcp" + url.substring(url.indexOf(":")) : url
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createAuthConfig(DockerRegistryCredentials registryCredentials) {
        Class authConfigClass = loadClass("${MODEL_PACKAGE}.AuthConfig")
        def authConfig = authConfigClass.newInstance()
        authConfig.registryAddress = registryCredentials.url
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
      Method bindParseMethod = bindClass.getMethod('parse', String.class)
      bindParseMethod.invoke(null, [path, volume].join(':'))
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
        Method rpParseMethod = rpClass.getMethod("parse", String.class)
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
            Method method = deviceClass.getMethod("parse", String)
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
    def createBuildImageResultCallback(Logger logger) {
        createPrintStreamProxyCallback(logger, createObject("${COMMAND_PACKAGE}.BuildImageResultCallback"))
    }

    @Override
    def createBuildImageResultCallback(Closure onNext) {
        createOnNextProxyCallback(onNext, createObject("${COMMAND_PACKAGE}.BuildImageResultCallback"))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPushImageResultCallback(Closure onNext) {
        def defaultHandler = createObject("${COMMAND_PACKAGE}.PushImageResultCallback")
        if (onNext) {
            return createOnNextProxyCallback(onNext, defaultHandler)
        }

        defaultHandler
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createPullImageResultCallback(Closure onNext) {
        def defaultHandler = createObject("${COMMAND_PACKAGE}.PullImageResultCallback")
        if (onNext) {
            return createOnNextProxyCallback(onNext, defaultHandler)
        }

        defaultHandler
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLoggingCallback(Logger logger) {
        final def delegate = createObject("${COMMAND_PACKAGE}.LogContainerResultCallback");
        def callback = new ByteBuddy()
                .with(new PrefixingRandom("helloworld"))
                .subclass(delegate.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("onNext" == method.name && args.length && args[0]) {
                            def frame = args[0]
                            switch (frame.streamType as String) {
                                case "STDOUT":
                                case "RAW":
                                    logger.quiet(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                                    break
                                case "STDERR":
                                    logger.error(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                                    break
                            }
                        }
                        try {
                            method.invoke(delegate, args)
                        } catch (InvocationTargetException e) {
                            throw e.cause
                        }
                    }   
                }))
                .make()
                .load(dockerClientClassLoader, Default.CHILD_FIRST)
                .getLoaded()
                .newInstance();
                
        println "!!!!!!"
        println "!!!!!!"
        println "!!!!!!"
        println "!!!!!! FOUND: ${callback.getClass().getClassLoader()}, default=${dockerClientClassLoader}"
        println "!!!!!! FOUND: ${callback.getClass()}, PRESENT: ${dockerClientClassLoader.getLoadedClasses().containsKey(callback.getClass().getName())}"
        println "!!!!!!"
        println "!!!!!!"
        println "!!!!!!"

        /*
        dockerClientClassLoader.getLoadedClasses().each { k, v ->
            println "POST-LOADED-CLASS: key=${k},value=${v}"
        }
        */
        callback
                
  /*
        enhancer.setClassLoader(dockerClientClassLoader)
        enhancer.setSuperclass(delegate.getClass())
        enhancer.setCallback([

            invoke: { Object proxy, java.lang.reflect.Method method, Object[] args ->
                if ("onNext" == method.name && args.length && args[0]) {
                    def frame = args[0]
                    switch (frame.streamType as String) {
                        case "STDOUT":
                        case "RAW":
                            logger.quiet(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                            break
                        case "STDERR":
                            logger.error(new String(frame.payload).replaceFirst(TRAILING_WHIESPACE, ''))
                            break
                    }
                }
                try {
                    method.invoke(delegate, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
            }

        ].asType(loadClass('net.sf.cglib.proxy.InvocationHandler')))

        def callback = enhancer.create()
        //dockerClientClassLoader.classes.put(callback.getClass().getName(), callback.getClass())
        callback
        */
    }

    @Override
    def createLoggingCallback(Closure onNext) {
        createOnNextProxyCallback(onNext, createObject("${COMMAND_PACKAGE}.LogContainerResultCallback"))
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createLoggingCallback(Writer sink) {
        final def delegate = createObject("${COMMAND_PACKAGE}.LogContainerResultCallback");
        new ByteBuddy()
                .with(new PrefixingRandom("helloworld"))
                .subclass(delegate.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("onNext" == method.name && args.length && args[0]) {
                            def frame = args[0]
                            switch (frame.streamType as String) {
                                case "STDOUT":
                                case "RAW":
                                case "STDERR":
                                    sink.append(new String(frame.payload))
                                    sink.flush()
                                    break
                            }
                        }
                        try {
                            method.invoke(delegate, args)
                        } catch (InvocationTargetException e) {
                            throw e.cause
                        }
                    }   
                }))
                .make()
                .load(dockerClientClassLoader, Default.CHILD_FIRST)
                .getLoaded()
                .newInstance(); 
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createExecCallback(OutputStream out, OutputStream err) {
        Class callbackClass = loadClass('com.github.dockerjava.core.command.ExecStartResultCallback')
        Constructor constructor = callbackClass.getConstructor(OutputStream, OutputStream)
        constructor.newInstance(out, err)
    }

    @Override
    def createExecCallback(Closure onNext) {
        final def delegate = createExecCallback(null, null)
        new ByteBuddy()
                .with(new PrefixingRandom("helloworld"))
                .subclass(delegate.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("onNext" == method.name) {
                            onNext.call(args[0])
                        }
                        try {
                            method.invoke(delegate, args)
                        } catch (InvocationTargetException e) {
                            throw e.cause
                        }
                    }   
                }))
                .make()
                .load(dockerClientClassLoader, Default.CHILD_FIRST)
                .getLoaded()
                .newInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    def createWaitContainerResultCallback(Closure onNext) {
        def defaultHandler = createObject("${COMMAND_PACKAGE}.WaitContainerResultCallback")
        if (onNext) {
            return createOnNextProxyCallback(onNext, defaultHandler)
        }

        defaultHandler
    }

    private createOnNextProxyCallback(Closure onNext, delegate) {
        new ByteBuddy()
                .with(new PrefixingRandom("helloworld"))
                .subclass(delegate.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("onNext" == method.name) {
                            onNext.call(args[0])
                        }
                        try {
                            method.invoke(delegate, args)
                        } catch (InvocationTargetException e) {
                            throw e.cause
                        }
                    }   
                }))
                .make()
                .load(dockerClientClassLoader, Default.CHILD_FIRST)
                .getLoaded()
                .newInstance();
    }

    private createPrintStreamProxyCallback(Logger logger, delegate) {
        new ByteBuddy()
                .with(new PrefixingRandom("helloworld"))
                .subclass(delegate.getClass())
                .method(ElementMatchers.any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("onNext" == method.name) {
                            def possibleStream = args[0].stream
                            if (possibleStream)
                                logger.quiet(possibleStream.trim())
                        }
                        try {
                            method.invoke(delegate, args)
                        } catch (InvocationTargetException e) {
                            throw e.cause
                        }
                    }   
                }))
                .make()
                .load(dockerClientClassLoader, Default.CHILD_FIRST)
                .getLoaded()
                .newInstance();
    }

    private Class loadInternetProtocolClass() {
        loadClass("${MODEL_PACKAGE}.InternetProtocol")
    }

    private Class loadBindClass() {
        loadClass("${MODEL_PACKAGE}.Bind")
    }

    private def parseDevice(String deviceString) {
        List<String> tokens = deviceString.tokenize(':')

        String permissions = 'rwm'
        String source = ''
        String destination = ''

        switch (tokens.size()) {
            case 3:
                if (validDeviceMode(tokens[2])) {
                    permissions = tokens[2]
                } else {
                    throw new IllegalArgumentException("Invalid device specification: " + deviceString)
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
                throw new IllegalArgumentException("Invalid device specification: " + deviceString)
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
            if (!validModes[mode]) {
                return false // wrong mode
            } else {
                validModes[mode] = false
            }
        }

        return true
    }
}
