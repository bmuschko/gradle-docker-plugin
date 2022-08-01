package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared

import static com.bmuschko.gradle.docker.TextUtils.escapeFilePath

class AbstractDockerRemoteApiTaskFunctionalTest extends AbstractGroovyDslFunctionalTest {
    public static final String USERNAME_SYSTEM_PROPERTY_KEY = 'registry.username'

    @Shared
    String username

    def setupSpec() {
        username = determineUsername()
    }

    def "Can create and execute custom remote API task with default extension values"() {
        buildFile << """
            task customDocker(type: CustomDocker)

            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
            import com.github.dockerjava.api.DockerClient

            class CustomDocker extends AbstractDockerRemoteApiTask {
                @Override
                void runRemoteCommand() {
                    assert dockerClient
                    assert dockerClient.dockerClientConfig.dockerHost.host == new URI('$TestConfiguration.dockerHost').host
                    assert dockerClient.dockerClientConfig.dockerHost.port == new URI('$TestConfiguration.dockerHost').port
                    assert dockerClient.dockerClientConfig.registryUrl == 'https://index.docker.io/v1/'
                    assert dockerClient.dockerClientConfig.registryUsername == '${username}'
                    assert !dockerClient.dockerClientConfig.registryPassword
                    assert !dockerClient.dockerClientConfig.registryEmail
                }
            }
        """

        when:
        build('customDocker')

        then:
        noExceptionThrown()
    }

    def "Can create and execute custom remote API task with extension values"() {
        File customCertPath = new File(temporaryFolder, 'mydocker')
        customCertPath.mkdirs()
        buildFile << """
            docker {
                url = 'tcp://remote.docker.com:2375'
                certPath = new File('${escapeFilePath(customCertPath.getCanonicalFile())}')

                registryCredentials {
                    url = 'https://some.registryCredentials.com/'
                    username = 'johnny'
                    password = 'pwd'
                    email = 'john.doe@gmail.com'
                }
            }

            task customDocker(type: CustomDocker)

            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
            import com.github.dockerjava.api.DockerClient

            class CustomDocker extends AbstractDockerRemoteApiTask {
                @Override
                void runRemoteCommand() {
                    assert dockerClient
                    assert dockerClient.dockerClientConfig.dockerHost == new URI('tcp://remote.docker.com:2375')
                    assert dockerClient.dockerClientConfig.registryUrl == 'https://index.docker.io/v1/'
                    assert dockerClient.dockerClientConfig.registryUsername == '${username}'
                    assert !dockerClient.dockerClientConfig.registryPassword
                    assert !dockerClient.dockerClientConfig.registryEmail
                }
            }
        """

        when:
        build('customDocker')

        then:
        noExceptionThrown()
    }

    def "Can use Docker client for UP-TO-DATE check in constructor of custom task [extension set before task]"() {
        buildFile << dockerExtensionWithCustomUrl()
        buildFile << dockerClientUsageInCustomTaskConstructor()

        when:
        BuildResult result = build('customDocker')

        then:
        result.task(':customDocker').outcome == TaskOutcome.SUCCESS
    }

    def "Can use Docker client for UP-TO-DATE check in constructor of custom task [extension set after task]"() {
        buildFile << dockerClientUsageInCustomTaskConstructor()
        buildFile << dockerExtensionWithCustomUrl()

        when:
        BuildResult result = build('customDocker')

        then:
        result.task(':customDocker').outcome == TaskOutcome.SUCCESS
    }

    private String determineUsername() {
        String usernameSystemProp = System.properties[USERNAME_SYSTEM_PROPERTY_KEY]

        if (usernameSystemProp) {
            return usernameSystemProp
        }

        // Docker Java 2.x properties file
        File dockerIoPropertiesFile = new File(System.getProperty('user.home'), '.docker.io.properties')
        String dockerIoUsername = readRegistryUsernameProperty(dockerIoPropertiesFile)

        if (dockerIoUsername) {
            return dockerIoUsername
        }

        // Docker Java 3.x properties file
        File dockerJavaPropertiesFile = new File(System.getProperty('user.home'), 'docker-java.properties')
        String dockerJavaUsername = readRegistryUsernameProperty(dockerJavaPropertiesFile)

        if (dockerJavaUsername) {
            return dockerJavaUsername
        }

        return System.properties['user.name']
    }

    private String readRegistryUsernameProperty(File propertiesFile) {
        if (propertiesFile.exists()) {
            Properties properties = new Properties()

            propertiesFile.withInputStream {
                properties.load(it)
            }

            return properties.getProperty(USERNAME_SYSTEM_PROPERTY_KEY)
        }

        return null
    }

    static String dockerClientUsageInCustomTaskConstructor() {
        """
            task customDocker(type: CustomDocker)

            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
            import com.github.dockerjava.api.DockerClient

            class CustomDocker extends AbstractDockerRemoteApiTask {
                CustomDocker() {
                    outputs.upToDateWhen {
                        assert dockerClient
                        assert dockerClient.dockerClientConfig.dockerHost == new URI('tcp://remote.docker.com:2375')
                        false
                    }
                }

                @Override
                void runRemoteCommand() {
                    // do nothing
                }
            }
        """
    }

    static String dockerExtensionWithCustomUrl() {
        """
            docker {
                url = 'tcp://remote.docker.com:2375'
            }
        """
    }
}
