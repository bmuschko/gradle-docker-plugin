package com.bmuschko.gradle.docker

import spock.lang.Requires
import spock.lang.Unroll

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
        dockerfile.text == expectedDockerFileContent(new ExpectedDockerfile())

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
                    tags = ['awesome-spring-boot:1.115']
                    jvmArgs = ['-Dspring.profiles.active=production', '-Xmx2048m']
                }
            }
        """

        when:
        build('buildAndCleanResources')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [9090, 8080], jvmArgs: ['-Dspring.profiles.active=production', '-Xmx2048m']))

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
                    tags = ['awesome-spring-boot:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, exposedPorts: []))

        where:
        plugin << REACTED_PLUGINS
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    @Unroll
    def "Can create image for a Spring Boot application and push it to DockerHub [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        DockerHubCredentials credentials = TestPrecondition.readDockerHubCredentials()
        buildFile << """
            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                    email = '$credentials.email'
                }

                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tags = ["\${docker.registryCredentials.username.get()}/springbootapp".toString()]
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))

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
                    tags = ['${TestConfiguration.dockerPrivateRegistryDomain}/springbootapp']
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE))

        where:
        plugin << REACTED_PLUGINS
    }

    private void writeSettingsFile() {
        settingsFile << groovySettingsFile()
    }

    private void setupSpringBootBuild(String reactedPluginIdentifier) {
        writeSpringBootBuildFile(reactedPluginIdentifier)
        configureRemoteApiPlugin()
        writeCustomTasksToBuildFile()
    }

    private void writeSpringBootBuildFile(String reactedPluginIdentifier) {
        buildFile << """
            plugins {
                id 'org.springframework.boot' version '2.0.3.RELEASE'
                id 'io.spring.dependency-management' version '1.0.5.RELEASE'
                id '$reactedPluginIdentifier'
                id 'com.bmuschko.docker-spring-boot-application'
            }

            version = '1.0'
            sourceCompatibility = 8
            targetCompatibility = 8
            
            repositories {
                jcenter()
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
ENTRYPOINT ${buildEntrypoint(expectedDockerfile.jvmArgs).collect { '"' + it + '"'} }
"""

        if (!expectedDockerfile.exposedPorts.isEmpty()) {
            dockerFileContent += """EXPOSE ${expectedDockerfile.exposedPorts.join(' ')}
"""
        }

        dockerFileContent
    }

    private static List<String> buildEntrypoint(List<String> jvmArgs) {
        List<String> entrypoint = ["java"]

        if (!jvmArgs.empty) {
            entrypoint.addAll(jvmArgs)
        }

        entrypoint.addAll(["-cp", "/app/resources:/app/classes:/app/libs/*", "com.bmuschko.gradle.docker.springboot.Application"])
        entrypoint
    }

    private static class ExpectedDockerfile {
        String baseImage = DEFAULT_BASE_IMAGE
        String maintainer = System.getProperty('user.name')
        List<String> exposedPorts = [8080]
        List<String> jvmArgs = []
    }
}
