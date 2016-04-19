package com.bmuschko.gradle.docker

import spock.lang.Shared

class AbstractDockerRemoteApiTaskFunctionalTest extends AbstractFunctionalTest {
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

            class CustomDocker extends AbstractDockerRemoteApiTask {
                @Override
                void runRemoteCommand(dockerClient) {
                    assert dockerClient
                    assert dockerClient.dockerClientConfig.uri == new URI('$TestConfiguration.dockerServerUrl')
                    assert dockerClient.dockerClientConfig.dockerCfgPath == "${System.properties['user.home']}/.dockercfg"
                    assert dockerClient.dockerClientConfig.serverAddress == 'https://index.docker.io/v1/'
                    assert dockerClient.dockerClientConfig.username == '${username}'
                    assert !dockerClient.dockerClientConfig.password
                    assert !dockerClient.dockerClientConfig.email
                }
            }
        """

        when:
        build('customDocker')

        then:
        noExceptionThrown()
    }

    def "Can create and execute custom remote API task with extension values"() {
        File customCertPath = temporaryFolder.newFolder('mydocker')
        buildFile << """
            docker {
                url = 'http://remote.docker.com:2375'
                certPath = new File('${customCertPath.canonicalPath}')

                registryCredentials {
                    url = 'https://some.registryCredentials.com/'
                    username = 'johnny'
                    password = 'pwd'
                    email = 'john.doe@gmail.com'
                }
            }

            task customDocker(type: CustomDocker)

            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask

            class CustomDocker extends AbstractDockerRemoteApiTask {
                @Override
                void runRemoteCommand(dockerClient) {
                    assert dockerClient
                    assert dockerClient.dockerClientConfig.uri == new URI('http://remote.docker.com:2375')
                    assert dockerClient.dockerClientConfig.dockerCfgPath == "${System.properties['user.home']}/.dockercfg"
                    assert dockerClient.dockerClientConfig.serverAddress == 'https://index.docker.io/v1/'
                    assert dockerClient.dockerClientConfig.username == '${username}'
                    assert !dockerClient.dockerClientConfig.password
                    assert !dockerClient.dockerClientConfig.email
                }
            }
        """

        when:
        build('customDocker')

        then:
        noExceptionThrown()
    }

    def "Can create and execute custom remote API task with callback class"() {
        buildFile << """
            buildscript{
                repositories {
                    mavenCentral()
                }
                dependencies{
                    classpath("com.github.docker-java:docker-java:2.2.3")
                }
            }

            import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.github.dockerjava.core.command.ExecStartResultCallback

            class CustomRemoteApiTask extends AbstractDockerRemoteApiTask {

                String containerId

                Integer exitCode

                void targetContainerId(Closure containerId) {
                    conventionMapping.containerId = containerId
                }

                @Override
                void runRemoteCommand(dockerClient) {
                    String id = getContainerId()

                    def execCreateCmdResponse = dockerClient
                            .execCreateCmd(id)
                            .withAttachStdout(true)
                            .withCmd('su', '-c', 'sleep 5 && exit 5')
                            .exec()

                    def callback = new ExecStartResultCallback(System.out, System.err);

                    dockerClient.execStartCmd(execCreateCmdResponse.getId())
                            .withDetach(false)
                            .exec(callback)
                            .awaitCompletion()

                    def execResponse = dockerClient
                            .inspectExecCmd(execCreateCmdResponse.getId())
                            .exec()

                    exitCode = execResponse.getExitCode()

                    if(exitCode != 5){
                        throw new GradleException("Wrong exit code. Should be 5, but was \$exitCode")
                    }
                }

                @Input
                String getContainerId() {
                    containerId
                }
            }

            task pullImage(type: DockerPullImage) {
                repository = 'alpine'
                tag = 'latest'
            }

            task createContainer(type: DockerCreateContainer){
                dependsOn pullImage
                targetImageId { pullImage.repository + ":" + pullImage.tag }
                stdinOpen = true
                cmd 'sh', '-c', 'read'
            }

            task startContainer(type: DockerStartContainer){
                dependsOn createContainer
                targetContainerId {createContainer.getContainerId()}
            }

            task stopContainer(type: DockerStopContainer){
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
            }

            task removeContainer(type: DockerRemoveContainer){
                dependsOn stopContainer
                targetContainerId {stopContainer.getContainerId()}
            }
            removeContainer.mustRunAfter stopContainer

            task customRemoteApiTask (type: CustomRemoteApiTask){
                dependsOn startContainer
                targetContainerId {startContainer.getContainerId()}
                finalizedBy stopContainer, removeContainer
            }
        """

        when:
        build('customRemoteApiTask')

        then:
        noExceptionThrown()
    }
    private String determineUsername() {
        String usernameSystemProp = System.properties[USERNAME_SYSTEM_PROPERTY_KEY]

        if(usernameSystemProp) {
            return usernameSystemProp
        }

        // Docker Java 2.x properties file
        File dockerIoPropertiesFile = new File(System.getProperty('user.home'), '.docker.io.properties')
        String dockerIoUsername = readRegistryUsernameProperty(dockerIoPropertiesFile)

        if(dockerIoUsername) {
            return dockerIoUsername
        }

        // Docker Java 3.x properties file
        File dockerJavaPropertiesFile = new File(System.getProperty('user.home'), 'docker-java.properties')
        String dockerJavaUsername = readRegistryUsernameProperty(dockerJavaPropertiesFile)

        if(dockerJavaUsername) {
            return dockerJavaUsername
        }

        return System.properties['user.name']
    }

    private String readRegistryUsernameProperty(File propertiesFile) {
        if(propertiesFile.exists()) {
            Properties properties = new Properties()

            propertiesFile.withInputStream {
                properties.load(it)
            }

            return properties.getProperty(USERNAME_SYSTEM_PROPERTY_KEY)
        }

        return null
    }
}
