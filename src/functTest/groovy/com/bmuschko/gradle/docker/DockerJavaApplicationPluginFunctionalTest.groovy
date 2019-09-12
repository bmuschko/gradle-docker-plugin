package com.bmuschko.gradle.docker

import spock.lang.Requires

import static com.bmuschko.gradle.docker.fixtures.DockerConventionPluginFixture.*
import static com.bmuschko.gradle.docker.fixtures.DockerJavaApplicationPluginFixture.writeJettyMainClass
import static com.bmuschko.gradle.docker.fixtures.DockerJavaApplicationPluginFixture.writePropertiesFile

class DockerJavaApplicationPluginFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def setup() {
        setupProjectUnderTest()
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
                    tags = ['jettyapp:1.115']
                    jvmArgs = ['-Xms256m', '-Xmx2048m']
                }
            }
        """

        when:
        build('buildAndCleanResources')

        then:
        assertGeneratedDockerfile(new ExpectedDockerfile(baseImage: CUSTOM_BASE_IMAGE, maintainer: 'benjamin.muschko@gmail.com', exposedPorts: [9090], jmvArgs: ['-Xms256m', '-Xmx2048m']))
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
                    tags = ['jettyapp:1.115']
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
                    tags = ['jettyapp:1.115']
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

    def "Can create image for Java application with additional files"() {
        given:
        temporaryFolder.newFile('file1.txt')
        temporaryFolder.newFile('file2.txt')

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
                    tags = ['jettyapp:1.115']
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
WORKDIR /app
COPY libs libs/
COPY classes classes/
ENTRYPOINT ["java", "-cp", "/app/resources:/app/classes:/app/libs/*", "com.bmuschko.gradle.docker.application.JettyMain"]
EXPOSE 9090
ADD file1.txt /some/dir/file1.txt
ADD file2.txt /other/dir/file2.txt
"""
        new File(buildContextDir(), 'file1.txt').exists()
        new File(buildContextDir(), 'file2.txt').exists()
        assertBuildContextLibs()
        assertBuildContextClasses()
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    def "Can create image for Java application and push to DockerHub"() {
        given:
        DockerHubCredentials credentials = TestPrecondition.readDockerHubCredentials()
        buildFile << """
            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                    email = '$credentials.email'
                }

                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tags = [ "\${docker.registryCredentials.username.get()}/javaapp".toString() ]
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

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to private registry"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tags = ['${TestConfiguration.dockerPrivateRegistryDomain}/javaapp']
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

    private void setupProjectUnderTest() {
        writeSettingsFile()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()
        writeJettyMainClass(projectDir)
    }

    private void writeSettingsFile() {
        settingsFile << groovySettingsFile()
    }

    private void writeBasicSetupToBuildFile() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: com.bmuschko.gradle.docker.DockerJavaApplicationPlugin

            version = '1.0'
            sourceCompatibility = 1.7

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.eclipse.jetty.aggregate:jetty-all:9.2.5.v20141112'
            }
        """
    }

    private void writeCustomTasksToBuildFile() {
        buildFile << imageTasks()
        buildFile << containerTasks()
        buildFile << lifecycleTask()
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
        assert dockerfile.text == generatedDockerfile(expectedDockerfile)
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
ENTRYPOINT ${buildEntrypoint(expectedDockerfile.jmvArgs).collect { '"' + it + '"'}}
"""

        if (!expectedDockerfile.exposedPorts.isEmpty()) {
            dockerfileContent += """EXPOSE ${expectedDockerfile.exposedPorts.join(' ')}
"""
        }

        dockerfileContent
    }

    private static List<String> buildEntrypoint(List<String> jvmArgs) {
        List<String> entrypoint = ["java"]

        if (!jvmArgs.empty) {
            entrypoint.addAll(jvmArgs)
        }

        entrypoint.addAll(["-cp", "/app/resources:/app/classes:/app/libs/*", "com.bmuschko.gradle.docker.application.JettyMain"])
        entrypoint
    }

    private void assertBuildContextLibs() {
        File libsDir = new File(buildContextDir(), 'libs')
        assert libsDir.isDirectory()
        assert libsDir.listFiles()*.name.containsAll(['javax.websocket-api-1.0.jar', 'jetty-all-9.2.5.v20141112.jar', 'javax.servlet-api-3.1.0.jar'])
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
    }
}
