package com.bmuschko.gradle.docker

import spock.lang.Requires
import spock.lang.Unroll

class DockerSpringBootApplicationPluginFunctionalTest extends AbstractFunctionalTest {
    private static final String PROJECT_NAME = 'spring-boot'
    private static final String CUSTOM_BASE_IMAGE = 'openjdk:8-alpine'
    private static final List<ReactedPlugin> REACTED_PLUGINS = [ReactedPlugin.WAR, ReactedPlugin.JAVA]

    def setup() {
        def settingsFile = temporaryFolder.newFile('settings.gradle')
        settingsFile << """
            rootProject.name = '$PROJECT_NAME'
        """
        createSpringBootApplicationClasses()
    }

    @Override
    protected void setupBuildfile() {
        buildFile = temporaryFolder.newFile('build.gradle')
    }

    @Unroll
    def "Can create image for Spring Boot application with default configuration [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)

        when:
        build('startContainer')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent('openjdk', plugin.archiveExtension, [8080])

        where:
        plugin << REACTED_PLUGINS
    }

    @Unroll
    def "Can create image for Spring Boot application with user-provided custom ports [#plugin.identifier plugin]"() {
        given:
        setupSpringBootBuild(plugin.identifier)
        buildFile << """
            docker {
                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    ports = [9090, 8080]
                    tag = 'awesome-spring-boot:1.115'
                }
            }
        """

        when:
        build('startContainer')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(CUSTOM_BASE_IMAGE, plugin.archiveExtension, [9090, 8080])

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
                    tag = 'awesome-spring-boot:1.115'
                }
            }
        """

        when:
        build('startContainer')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(CUSTOM_BASE_IMAGE, plugin.archiveExtension, [])

        where:
        plugin << REACTED_PLUGINS
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    @Unroll
    def "Can create image for a Spring Boot application and push it to DockerHub [#plugin.identifier plugin]"() {
        DockerHubCredentials credentials = TestPrecondition.readDockerHubCredentials()
        setupSpringBootBuild(plugin.identifier)
        buildFile << """
            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                    email = '$credentials.email'
                }

                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tag = "\$docker.registryCredentials.username/springbootapp"
                }
            }
        """

        when:
        build('dockerPushImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(CUSTOM_BASE_IMAGE, plugin.archiveExtension)

        where:
        plugin << REACTED_PLUGINS
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    @Unroll
    def "Can create image for a Spring Boot application and push it to private registry [#plugin.identifier plugin]"() {
        setupSpringBootBuild(plugin.identifier)
        buildFile << """
            docker {
                springBootApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tag = '${TestConfiguration.dockerPrivateRegistryDomain}/springbootapp'
                }
            }
        """

        when:
        build('dockerPushImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == expectedDockerFileContent(CUSTOM_BASE_IMAGE, plugin.archiveExtension)

        where:
        plugin << REACTED_PLUGINS
    }

    private File dockerFile() {
        new File(projectDir, 'build/docker/Dockerfile')
    }

    static String expectedDockerFileContent(String image, String archiveExtension) {
        expectedDockerFileContent(image, archiveExtension, [8080])
    }

    static String expectedDockerFileContent(String image, String archiveExtension, List<String> ports) {
        String dockerFileContent = """FROM $image
COPY ${PROJECT_NAME}-1.0.${archiveExtension} /app/$PROJECT_NAME-1.0.${archiveExtension}
ENTRYPOINT ["java"]
CMD ["-jar", "/app/${PROJECT_NAME}-1.0.${archiveExtension}"]
"""

        if(!ports.empty) {
            dockerFileContent += """EXPOSE ${ports.join(' ')}
"""
        }

        dockerFileContent
    }

    private void createSpringBootApplicationClasses() {
        File packageDir = temporaryFolder.newFolder('src', 'main', 'java', 'com', 'bmuschko', 'gradle', 'docker', 'springboot')
        File helloControllerClass = new File(packageDir, 'HelloController.java')
        helloControllerClass.createNewFile()
        helloControllerClass << """
            package com.bmuschko.gradle.docker.springboot;
            
            import org.springframework.web.bind.annotation.RestController;
            import org.springframework.web.bind.annotation.RequestMapping;
            
            @RestController
            public class HelloController {
            
                @RequestMapping("/")
                public String index() {
                    return "Greetings from Spring Boot!";
                }
            
            }
        """
        File applicationClass = new File(packageDir, 'Application.java')
        applicationClass.createNewFile()
        applicationClass << """
            package com.bmuschko.gradle.docker.springboot;
            
            import java.util.Arrays;

            import org.springframework.boot.CommandLineRunner;
            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            import org.springframework.context.ApplicationContext;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                }
            
                @Bean
                public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
                    return args -> {
                        System.out.println("Let's inspect the beans provided by Spring Boot:");
                        String[] beanNames = ctx.getBeanDefinitionNames();
                        Arrays.sort(beanNames);
                        for (String beanName : beanNames) {
                            System.out.println(beanName);
                        }
                    };
                }
            }
        """
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
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer

            task inspectImage(type: DockerInspectImage) {
                dependsOn dockerBuildImage
                targetImageId { dockerBuildImage.getImageId() }
            }

            task createContainer(type: DockerCreateContainer) {
                dependsOn inspectImage
                targetImageId { dockerBuildImage.getImageId() }
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId { createContainer.getContainerId() }
            }
        """
    }

    enum ReactedPlugin {
        WAR('war', 'war'), JAVA('java', 'jar')

        private final String identifier
        private final String archiveExtension

        ReactedPlugin(String identifier, String archiveExtension) {
            this.identifier = identifier
            this.archiveExtension = archiveExtension
        }

        String getIdentifier() {
            identifier
        }

        String getArchiveExtension() {
            archiveExtension
        }
    }
}
