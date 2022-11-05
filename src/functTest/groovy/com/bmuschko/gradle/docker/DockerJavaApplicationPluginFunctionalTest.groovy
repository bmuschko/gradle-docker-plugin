package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

import static com.bmuschko.gradle.docker.TextUtils.equalsIgnoreLineEndings
import static com.bmuschko.gradle.docker.fixtures.DockerConventionPluginFixture.*
import static com.bmuschko.gradle.docker.fixtures.DockerJavaApplicationPluginFixture.writeJettyMainClass
import static com.bmuschko.gradle.docker.fixtures.DockerJavaApplicationPluginFixture.writePropertiesFile

class DockerJavaApplicationPluginFunctionalTest extends AbstractGroovyDslFunctionalTest {

    public static final String DOCKER_CONFIG = 'DOCKER_CONFIG'
    public static final String USER_PASS_CONFIG = 'src/functTest/resources/username_password'
    public static final String BASIC_AUTH_CONFIG = 'src/functTest/resources/basic_auth'

    def setup() {
        setupProjectUnderTest()
    }

    def "Can create image and start container for Java application with default configuration and configuration cache enabled"() {
        when:
        BuildResult result = build('buildAndCleanResources')

        then:
        assertGeneratedDockerfile()
        assertBuildContextLibs()
        assertBuildContextClasses()
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build('buildAndCleanResources')

        then:
        result.output.contains("Configuration cache entry reused.")
    }

    def "Can create image and start container for Java application with default configuration"() {
        when:
        build('buildAndCleanResources')

        then:
        assertGeneratedDockerfile()
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    def "Can create image and start container for Java application with resource file"() {
        given:
        writePropertiesFile(projectDir)

        when:
        build('buildAndCleanResources')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(copyResources: true))
        assertBuildContextLibs()
        assertBuildContextResources()
        assertBuildContextClasses()
    }

    def "Can create image and start container for Java application with user-driven configuration"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = [9090]
                    images = ['jettyapp:1.115']
                    jvmArgs = ['-Xms256m', '-Xmx2048m']
                    args = ['--my.config.name=myproject']
                }
            }
        """

        when:
        build('buildAndCleanResources')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [9090], jmvArgs: ['-Xms256m', '-Xmx2048m'], args: ['--my.config.name=myproject']))
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    def "Can create image for Java application with user-driven configuration with several ports"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = [9090, 8080]
                    images = ['jettyapp:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [9090, 8080], jmvArgs: []))
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    def "Can create image for Java application with user-driven configuration without exposed ports"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = []
                    images = ['jettyapp:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [], jmvArgs: []))
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    def "Can provide a specific main class name"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    mainClassName = 'com.bmuschko.custom.Main'
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(mainClassName: 'com.bmuschko.custom.Main'))
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    def "Can create image for Java application with additional files"() {
        given:
        new File(temporaryFolder, 'file1.txt').createNewFile()
        new File(temporaryFolder, 'file2.txt').createNewFile()

        buildFile << """
            dockerSyncBuildContext {
                from file('file1.txt')
                from file('file2.txt')
            }

            dockerCreateDockerfile {
                addFile 'file1.txt', '/some/dir/file1.txt'
                addFile 'file2.txt', '/other/dir/file2.txt'
            }

            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = [9090]
                    images = ['jettyapp:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
WORKDIR /app
COPY libs libs/
COPY classes classes/
ENTRYPOINT ["java", "-cp", "/app/resources:/app/classes:/app/libs/*", "com.bmuschko.gradle.docker.application.JettyMain"]
EXPOSE 9090
ADD file1.txt /some/dir/file1.txt
ADD file2.txt /other/dir/file2.txt
""")
        new File(buildContextDir(), 'file1.txt').exists()
        new File(buildContextDir(), 'file2.txt').exists()
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    def "Can create image for Java application and push to DockerHub"() {
        given:
        RegistryCredentials credentials = TestPrecondition.readDockerHubCredentials()
        buildFile << """
            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                }

                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = ["\${docker.registryCredentials.username.get()}/javaapp:1.2.3".toString(), "\${docker.registryCredentials.username.get()}/javaapp:latest".toString()]
                }
            }
        """

        when:
        BuildResult result = build('pushAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))
        assertBuildContextLibs()
        assertBuildContextClasses()
        result.output.contains("Pushing image '$credentials.username/javaapp:1.2.3'")
        result.output.contains("Pushing image '$credentials.username/javaapp:latest'")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to private registry"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = ['${TestConfiguration.dockerPrivateRegistryDomain}/javaapp:1.2.3', '${TestConfiguration.dockerPrivateRegistryDomain}/javaapp:latest']
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to secure registry using username password"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = [
                        '${TestConfiguration.dockerPrivateSecureRegistryDomain}/javaapp:1.2.3',
                        '${TestConfiguration.dockerPrivateSecureRegistryDomain}/javaapp:latest'
                    ]
                }
            }
        """
        addEnvVar(DOCKER_CONFIG, USER_PASS_CONFIG)

        when:
        build('pushAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))
        assertBuildContextLibs()
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to secure registry using basic auth"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = [
                        '${TestConfiguration.dockerPrivateSecureRegistryDomain}/javaapp:1.2.3',
                        '${TestConfiguration.dockerPrivateSecureRegistryDomain}/javaapp:latest'
                    ]
                }
            }
        """
        addEnvVar(DOCKER_CONFIG, BASIC_AUTH_CONFIG)

        when:
        build('pushAndRemoveImage')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))
        assertBuildContextLibs()
    }

    def "Can map images from to build and push tasks"() {
        given:
        buildFile << """
            def expectedImages = ['javaapp:1.2.3', 'javaapp:latest'] as Set<String>

            docker {
                javaApplication {
                    images = expectedImages
                }
            }

            task verify {
                doLast {
                    assert dockerBuildImage.images.get() == expectedImages
                    assert dockerPushImage.images.get() == expectedImages
                }
            }
        """

        expect:
        build('verify')
    }

    def "does not realize all possible tasks"() {
        when:
        writeNoTasksRealizedAssertionToBuildFile()

        then:
        build('help')
    }

    def "does not realize all RegistryCredentialsAware tasks"() {
        when:
        writeNoTasksRealizedAssertionToBuildFile()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            tasks.register('customBuildImage', DockerBuildImage)
        """

        then:
        build('help')
    }

    private void setupProjectUnderTest() {
        writeSettingsFile()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()
        writeJettyMainClass(projectDir)
        writeJettyMainClass()
    }

    private void writeJettyMainClass() {
        buildFile << """
            docker {
                javaApplication {
                    mainClassName = 'com.bmuschko.gradle.docker.application.JettyMain'
                }
            }
            """
    }

    private void writeSettingsFile() {
        settingsFile << groovySettingsFile()
    }

    private void writeBasicSetupToBuildFile() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: com.bmuschko.gradle.docker.DockerJavaApplicationPlugin

            version = '1.0'
            sourceCompatibility = 8
            targetCompatibility = 8

            dependencies {
                implementation 'org.eclipse.jetty.aggregate:jetty-all:9.4.29.v20200521'
            }
        """
    }

    private void writeCustomTasksToBuildFile() {
        buildFile << imageTasks()
        buildFile << containerTasks()
        buildFile << lifecycleTask()
    }

    private void writeNoTasksRealizedAssertionToBuildFile() {
        buildFile << """
            tasks.configureEach {
                assert it.path in [':help', ':clean']
            }
        """
    }

    private File dockerFile() {
        new File(buildContextDir(), 'Dockerfile')
    }

    private File buildContextDir() {
        new File(projectDir, 'build/docker')
    }

    private void assertGeneratedDockerfile(ExpectedDockerfile expectedDockerfile = new ExpectedDockerfile()) {
        File dockerfile = dockerFile()
        assert dockerfile.exists()
        assert equalsIgnoreLineEndings(dockerfile.text, generatedDockerfile(expectedDockerfile))
    }

    private String generatedDockerfile(ExpectedDockerfile expectedDockerfile) {
        String dockerfileContent = """FROM $expectedDockerfile.baseImage
LABEL maintainer=$expectedDockerfile.maintainer
WORKDIR /app
"""
        if (expectedDockerfile.copyLibs) {
            dockerfileContent += """COPY libs libs/
"""
        }

        if (expectedDockerfile.copyResources) {
            dockerfileContent += """COPY resources resources/
"""
        }

        dockerfileContent += """COPY classes classes/
ENTRYPOINT ${buildEntrypoint(expectedDockerfile.jmvArgs, expectedDockerfile.mainClassName, expectedDockerfile.args).collect { '"' + it + '"'}}
"""

        if (!expectedDockerfile.exposedPorts.isEmpty()) {
            dockerfileContent += """EXPOSE ${expectedDockerfile.exposedPorts.join(' ')}
"""
        }

        dockerfileContent
    }

    private static List<String> buildEntrypoint(List<String> jvmArgs, String mainClassName, List<String> args) {
        List<String> entrypoint = ["java"]

        if (!jvmArgs.empty) {
            entrypoint.addAll(jvmArgs)
        }

        entrypoint.addAll(["-cp", "/app/resources:/app/classes:/app/libs/*", mainClassName])

        if (!args.empty) {
            entrypoint.addAll(args)
        }

        entrypoint
    }

    private void assertBuildContextLibs() {
        File libsDir = new File(buildContextDir(), 'libs')
        assert libsDir.isDirectory()
        assert libsDir.listFiles()*.name.containsAll(['javax.websocket-api-1.0.jar', 'jetty-servlet-9.4.29.v20200521.jar', 'javax.servlet-api-3.1.0.jar'])
    }

    private void assertBuildContextResources() {
        File resourcesDir = new File(buildContextDir(), 'resources')
        assert resourcesDir.isDirectory()
        assert new File(resourcesDir, 'my.properties').isFile()
    }

    private void assertBuildContextClasses() {
        File classesDir = new File(buildContextDir(), 'classes')
        assert classesDir.isDirectory()
        assert new File(classesDir, 'com/bmuschko/gradle/docker/application/JettyMain.class').isFile()
    }

    private static class ExpectedDockerfile {
        String baseImage = DEFAULT_BASE_IMAGE
        String maintainer = System.getProperty('user.name')
        boolean copyLibs = true
        boolean copyResources = false
        List<String> exposedPorts = [8080]
        List<String> jmvArgs = []
        String mainClassName = 'com.bmuschko.gradle.docker.application.JettyMain'
        List<String> args = []
    }
}
