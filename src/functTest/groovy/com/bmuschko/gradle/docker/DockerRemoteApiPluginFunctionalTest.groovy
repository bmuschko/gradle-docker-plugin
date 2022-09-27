package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Ignore
import spock.lang.Requires

import static com.bmuschko.gradle.docker.fixtures.DockerConventionPluginFixture.groovySettingsFile

class DockerRemoteApiPluginFunctionalTest extends AbstractGroovyDslFunctionalTest {

    public static final String DEFAULT_USERNAME = 'Jon Doe'
    public static final String DEFAULT_PASSWORD = 'pwd'
    public static final String CUSTOM_URL = 'https://myregistry.com/v2/'
    public static final String CUSTOM_USERNAME = 'Sally Wash'
    public static final String CUSTOM_PASSWORD = 'secret'
    public static final String DOCKER_CONFIG = 'DOCKER_CONFIG'
    public static final String WRONG_USER_PASS_CONFIG = 'src/functTest/resources/wrong_username_password'
    public static final String SECURE_REGISTRY_USER = "testuser"
    public static final String SECURE_REGISTRY_PASSWD = "testpassword"
    public static final String TEST_DOCKER_IMAGE_WITH_TAG = "gradle-docker-test:latest"

    def setup() {
        settingsFile << groovySettingsFile()
    }

    def "can automatically use extension credentials in registry-aware custom tasks"() {
        given:
        buildFile << registryCredentials()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

            task buildImage(type: DockerBuildImage)
            task pullImage(type: DockerPullImage)
            task pushImage(type: DockerPushImage)

            task verify {
                doLast {
                    def registryCredentialsAwareTasks = tasks.withType(RegistryCredentialsAware)
                    assert registryCredentialsAwareTasks.size() == 3

                    registryCredentialsAwareTasks.each { task ->
                        assert task.registryCredentials.username.get() == '$DEFAULT_USERNAME'
                        assert task.registryCredentials.password.get() == '$DEFAULT_PASSWORD'
                    }
                }
            }
        """

        expect:
        build('verify')
    }

    def "can overwrite default credentials for custom tasks with action"() {
        given:
        buildFile << registryCredentials()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

            task buildImage(type: DockerBuildImage) {
                registryCredentials {
                    url = '$CUSTOM_URL'
                    username = '$CUSTOM_USERNAME'
                    password = '$CUSTOM_PASSWORD'
                }
            }

            task pullImage(type: DockerPullImage) {
                registryCredentials {
                    url = '$CUSTOM_URL'
                    username = '$CUSTOM_USERNAME'
                    password = '$CUSTOM_PASSWORD'
                }
            }

            task pushImage(type: DockerPushImage) {
                registryCredentials {
                    url = '$CUSTOM_URL'
                    username = '$CUSTOM_USERNAME'
                    password = '$CUSTOM_PASSWORD'
                }
            }

            task verify {
                doLast {
                    def registryCredentialsAwareTasks = tasks.withType(RegistryCredentialsAware)
                    assert registryCredentialsAwareTasks.size() == 3

                    registryCredentialsAwareTasks.each { task ->
                        assert task.registryCredentials.url.get() == '$CUSTOM_URL'
                        assert task.registryCredentials.username.get() == '$CUSTOM_USERNAME'
                        assert task.registryCredentials.password.get() == '$CUSTOM_PASSWORD'
                    }
                }
            }
        """

        expect:
        build('verify')
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE })
    def "can push and pull images overriding wrong registryCredentials in task"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            
            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                runCommand("echo ${UUID.randomUUID()}")
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy { removeImage }
                images = [
                    '${TestConfiguration.dockerPrivateSecureRegistryDomain}/${TEST_DOCKER_IMAGE_WITH_TAG}'
                ]
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                images = buildImage.images
                registryCredentials {
                    url = '${TestConfiguration.dockerPrivateSecureRegistryDomain}'
                    username = '$SECURE_REGISTRY_USER'
                    password = '$SECURE_REGISTRY_PASSWD'
                }
            }

            task pullImage(type: DockerPullImage) {
                dependsOn pushImage
                image = '${TestConfiguration.dockerPrivateSecureRegistryDomain}/${TEST_DOCKER_IMAGE_WITH_TAG}'
                registryCredentials {
                    url = '${TestConfiguration.dockerPrivateSecureRegistryDomain}'
                    username = '$SECURE_REGISTRY_USER'
                    password = '$SECURE_REGISTRY_PASSWD'
                }
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn buildImage
                mustRunAfter pushImage, pullImage
                targetImageId buildImage.getImageId()
                force = true
            }
        """

        addEnvVar(DOCKER_CONFIG, WRONG_USER_PASS_CONFIG)

        when:
        BuildResult result = build('pullImage')

        then:
        result.output.contains("Pushing image '${TestConfiguration.dockerPrivateSecureRegistryDomain}/${TEST_DOCKER_IMAGE_WITH_TAG}' to ${TestConfiguration.dockerPrivateSecureRegistryDomain}")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_SECURE_REGISTRY_REACHABLE })
    def "can push and pull images overriding wrong registryCredentials with docker extension"() {
        given:
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile

            docker {
                registryCredentials {
                    url = '${TestConfiguration.dockerPrivateSecureRegistryDomain}'
                    username = '$SECURE_REGISTRY_USER'
                    password = '$SECURE_REGISTRY_PASSWD'
                }
            }

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                runCommand("echo ${UUID.randomUUID()}")
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy { removeImage }
                images = [
                    '${TestConfiguration.dockerPrivateSecureRegistryDomain}/${TEST_DOCKER_IMAGE_WITH_TAG}'
                ]
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                images = buildImage.images
            }

            task pullImage(type: DockerPullImage) {
                dependsOn pushImage
                image = '${TestConfiguration.dockerPrivateSecureRegistryDomain}/${TEST_DOCKER_IMAGE_WITH_TAG}'
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn buildImage
                mustRunAfter pushImage, pullImage
                targetImageId buildImage.getImageId()
                force = true
            }
        """

        addEnvVar(DOCKER_CONFIG, WRONG_USER_PASS_CONFIG)

        expect:
        build('pullImage')
    }

    def "can convert credentials into PasswordCredentials type and retrieve values"() {
        given:
        buildFile << registryCredentials()
        buildFile << """
            task convert {
                doLast {
                    def passwordCredentials = docker.registryCredentials.asPasswordCredentials()
                    assert passwordCredentials instanceof org.gradle.api.credentials.PasswordCredentials
                    assert passwordCredentials.username == '$DEFAULT_USERNAME'
                    assert passwordCredentials.password == '$DEFAULT_PASSWORD'
                }
            }
        """

        expect:
        build('convert')
    }

    def "can convert credentials into PasswordCredentials type and change values"() {
        given:
        buildFile << registryCredentials()
        buildFile << """
            task convert {
                doLast {
                    def passwordCredentials = docker.registryCredentials.asPasswordCredentials()
                    assert passwordCredentials instanceof org.gradle.api.credentials.PasswordCredentials
                    passwordCredentials.username = '$CUSTOM_USERNAME'
                    passwordCredentials.password = '$CUSTOM_PASSWORD'
                    assert passwordCredentials.username == '$CUSTOM_USERNAME'
                    assert passwordCredentials.password == '$CUSTOM_PASSWORD'
                    assert docker.registryCredentials.username.get() == '$CUSTOM_USERNAME'
                    assert docker.registryCredentials.password.get() == '$CUSTOM_PASSWORD'
                }
            }
        """

        expect:
        build('convert')
    }

    @Ignore
    @Requires({ TestPrecondition.HARBOR_CREDENTIALS_AVAILABLE })
    def "can push image to Harbor"() {
        given:
        RegistryCredentials credentials = TestPrecondition.readHarborCredentials()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            
            task pullImage(type: DockerPullImage) {
                image = 'alpine:3.15.0'
            }
            
            task dockerfile(type: Dockerfile) {
                dependsOn pullImage
                from 'alpine:3.15.0'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                images = ['demo.goharbor.io/gradle-docker-plugin/alpine:3.15.0']
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId buildImage.imageId
                force = true
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                finalizedBy removeImage
                images = buildImage.images
                
                registryCredentials {
                    url = 'https://demo.goharbor.io/v2/'
                    username = '$credentials.username'
                    password = '$credentials.password'
                }
            }
        """

        when:
        BuildResult result = build('pushImage')

        then:
        result.output.contains("Pushing image 'demo.goharbor.io/gradle-docker-plugin/alpine:3.15.0' to demo.goharbor.io")
    }

    static String registryCredentials() {
        """
            docker {
                registryCredentials {
                    username = '$DEFAULT_USERNAME'
                    password = '$DEFAULT_PASSWORD'
                }
            }
        """
    }
}
