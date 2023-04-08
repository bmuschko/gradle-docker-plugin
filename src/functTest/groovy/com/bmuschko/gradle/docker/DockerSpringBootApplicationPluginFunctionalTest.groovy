package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires
import spock.lang.Unroll

import static com.bmuschko.gradle.docker.TextUtils.equalsIgnoreLineEndings
import static com.bmuschko.gradle.docker.fixtures.DockerConventionPluginFixture.*
import static com.bmuschko.gradle.docker.fixtures.DockerSpringBootApplicationPluginFixture.writeSpringBootApplicationClasses

class DockerSpringBootApplicationPluginFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final List<ReactedPlugin> REACTED_PLUGINS = [ReactedPlugin.WAR, ReactedPlugin.JAVA]

    def setup() {
        writeSettingsFile()
        writeSpringBootApplicationClasses(projectDir)
    }

    @Override
    protected void setupBuildfile() {
    }

    @Unroll
    def "Can create image and start container for Spring Boot application with default configuration [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        when:
        build('buildAndCleanResources')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile()))

        where:
        plugin << REACTED_PLUGINS
    }

    @Unroll
    def "Can create image and start container for Spring Boot application with user-provided configuration [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        buildFile << """
            docker {
                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = [9090, 8080]
                    images = ['awesome-spring-boot:1.115']
                    jvmArgs = ['-Dspring.profiles.active=production', '-Xmx2048m']
                    args = ['--spring.config.name=myproject']
                }
            }
        """

        when:
        build('buildAndCleanResources')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [9090, 8080], jvmArgs: ['-Dspring.profiles.active=production', '-Xmx2048m'], args: ['--spring.config.name=myproject'])))

        where:
        plugin << REACTED_PLUGINS
    }

    @Unroll
    def "Can create image for Spring Boot application with user-provided empty ports [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        buildFile << """
            docker {
                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    ports = []
                    images = ['awesome-spring-boot:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, exposedPorts: [])))

        where:
        plugin << REACTED_PLUGINS
    }

    @Unroll
    def "Can provide a specific main class name [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        buildFile << """
            docker {
                springBootApplication {
                    mainClassName = 'com.bmuschko.custom.Main'
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile(mainClassName: 'com.bmuschko.custom.Main')))

        where:
        plugin << REACTED_PLUGINS
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    @Unroll
    def "Can create image for a Spring Boot application and push it to DockerHub [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        RegistryCredentials credentials = TestPrecondition.readDockerHubCredentials()
        buildFile << """
            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                }

                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = ["\${docker.registryCredentials.username.get()}/springbootapp:1.2.3".toString(), "\${docker.registryCredentials.username.get()}/springbootapp:latest".toString()]
                }
            }
        """

        when:
        BuildResult result = build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE)))
        result.output.contains("Pushing image '$credentials.username/springbootapp:1.2.3'")
        result.output.contains("Pushing image '$credentials.username/springbootapp:latest'")

        where:
        plugin << REACTED_PLUGINS
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    @Unroll
    def "Can create image for a Spring Boot application and push it to private registry [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        buildFile << """
            docker {
                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    images = ['${TestConfiguration.dockerPrivateRegistryDomain}/springbootapp:1.2.3', '${TestConfiguration.dockerPrivateRegistryDomain}/springbootapp:latest']
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        equalsIgnoreLineEndings(dockerfile.text, expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE)))

        where:
        plugin << REACTED_PLUGINS
    }

    def "Can map images from to build and push tasks"() {
        given:
        setupSpringBootBuild(ReactedPlugin.JAVA.identifier)

        buildFile << """
            def expectedImages = ['bmuschko/springbootapp:1.2.3', 'bmuschko/springbootapp:latest'] as Set<String>

            docker {
                springBootApplication {
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

    private void writeSettingsFile() {
        settingsFile << groovySettingsFile()
    }

    private void setupSpringBootBuild(String reactedPluginIdentifier) {
        writeSpringBootBuildFile(reactedPluginIdentifier)
        configureRemoteApiPlugin()
        writeCustomTasksToBuildFile()
        writeSpringBootApplicationClass()
    }

    private void writeSpringBootBuildFile(String reactedPluginIdentifier) {
        buildFile << """
            plugins {
                id 'org.springframework.boot' version '2.7.5'
                id 'io.spring.dependency-management' version '1.1.0'
                id '$reactedPluginIdentifier'
                id 'com.bmuschko.docker-spring-boot-application'
            }

            version = '1.0'
            sourceCompatibility = 8
            targetCompatibility = 8

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web'
            }
        """
    }

    private void writeCustomTasksToBuildFile() {
        buildFile << imageTasks()
        buildFile << containerTasks()
        buildFile << lifecycleTask()
    }


    void writeSpringBootApplicationClass() {
        buildFile << """
            docker {
                springBootApplication {
                    mainClassName = 'com.bmuschko.gradle.docker.springboot.Application'
                }
            }
            """
    }

    enum ReactedPlugin {
        WAR('war'), JAVA('java')

        private final String identifier

        ReactedPlugin(String identifier) {
            this.identifier = identifier
        }

        String getIdentifier() {
            identifier
        }
    }

    private File dockerFile() {
        new File(projectDir, 'build/docker/Dockerfile')
    }

    private static String expectedDockerFileContent(ExpectedDockerfile expectedDockerfile) {
        String dockerFileContent = """FROM $expectedDockerfile.baseImage
LABEL maintainer=$expectedDockerfile.maintainer
WORKDIR /app
COPY libs libs/
COPY classes classes/
ENTRYPOINT ${buildEntrypoint(expectedDockerfile.jvmArgs, expectedDockerfile.mainClassName, expectedDockerfile.args).collect { '"' + it + '"'} }
"""

        if (!expectedDockerfile.exposedPorts.isEmpty()) {
            dockerFileContent += """EXPOSE ${expectedDockerfile.exposedPorts.join(' ')}
"""
        }

        dockerFileContent
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

    private static class ExpectedDockerfile {
        String baseImage = DEFAULT_BASE_IMAGE
        String maintainer = System.getProperty('user.name')
        List<String> exposedPorts = [8080]
        List<String> jvmArgs = []
        String mainClassName = 'com.bmuschko.gradle.docker.springboot.Application'
        List<String> args = []
    }
}
