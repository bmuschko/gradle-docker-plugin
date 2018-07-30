package com.bmuschko.gradle.docker

import spock.lang.Requires

import static com.bmuschko.gradle.docker.DockerConventionPluginFixture.*

class DockerJavaApplicationPluginFunctionalTest extends AbstractFunctionalTest {

    def setup() {
        setupProjectUnderTest()
    }

    def "Can create image and start container for Java application with default configuration"() {
        when:
        build('buildAndCleanResources')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $DEFAULT_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
EXPOSE 8080
"""
    }

    def "Can create image and start container for Java application with user-driven configuration"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    port = 9090
                    tag = 'jettyapp:1.115'
                }
            }
        """

        when:
        build('buildAndCleanResources')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
EXPOSE 9090
"""
    }

    def "Can create image for Java application with user-driven configuration with several ports"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = [9090, 8080]
                    tag = 'jettyapp:1.115'
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
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
EXPOSE 9090 8080
"""
    }

    def "Can create image for Java application with user-driven configuration without exposed ports"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    ports = []
                    tag = 'jettyapp:1.115'
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
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
"""
    }

    def "Can create image for Java application with user-driven configuration with custom cmd/entrypoint"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    port = 9090
                    tag = 'jettyapp:1.115'
                    exec {
                        defaultCommand 'arg1'
                        entryPoint 'bin/run.sh'
                    }
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
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
CMD ["arg1"]
ENTRYPOINT ["bin/run.sh"]
EXPOSE 9090
"""
    }

    def "Can create image for Java application with user-driven configuration with no cmd/entrypoint"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    port = 9090
                    tag = 'jettyapp:1.115'
                    ${execDirective}
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
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar

EXPOSE 9090
"""

        where:
        execDirective << ['exec null',
                          'exec {}']
    }

    def "Can create image for Java application with additional files"() {
        given:
        temporaryFolder.newFile('file1.txt')
        temporaryFolder.newFile('file2.txt')

        buildFile << """
            dockerCopyDistResources {
                from file('file1.txt')
                from file('file2.txt')
            }

            dockerDistTar {
                addFile 'file1.txt', '/some/dir/file1.txt'
                addFile 'file2.txt', '/other/dir/file2.txt'
            }

            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'benjamin.muschko@gmail.com'
                    port = 9090
                    tag = 'jettyapp:1.115'
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
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
ADD file1.txt /some/dir/file1.txt
ADD file2.txt /other/dir/file2.txt
EXPOSE 9090
"""
        new File(projectDir, 'build/docker/file1.txt').exists()
        new File(projectDir, 'build/docker/file2.txt').exists()
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    def "Can create image for Java application and push to DockerHub"() {
        given:
        DockerHubCredentials credentials = TestPrecondition.readDockerHubCredentials()
        buildFile << """
            applicationName = 'javaapp'

            docker {
                registryCredentials {
                    username = '$credentials.username'
                    password = '$credentials.password'
                    email = '$credentials.email'
                }

                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tag = "\$docker.registryCredentials.username/javaapp"
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD javaapp /javaapp
ADD app-lib/${PROJECT_NAME}-1.0.jar /javaapp/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/javaapp/bin/javaapp"]
EXPOSE 8080
"""
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to private registry"() {
        given:
        buildFile << """
            applicationName = 'javaapp'

            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tag = '${TestConfiguration.dockerPrivateRegistryDomain}/javaapp'
                }
            }
        """

        when:
        build('pushAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD javaapp /javaapp
ADD app-lib/${PROJECT_NAME}-1.0.jar /javaapp/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/javaapp/bin/javaapp"]
EXPOSE 8080
"""
    }

    def "Can create image without MAINTAINER"() {
        given:
        buildFile << """
            docker {
                javaApplication {
                    skipMaintainer = true
                }
            }
        """

        when:
        build('buildAndRemoveImage')

        then:
        File dockerfile = dockerFile()
        dockerfile.exists()
        dockerfile.text == """FROM $DEFAULT_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD ${PROJECT_NAME} /${PROJECT_NAME}
ADD app-lib/${PROJECT_NAME}-1.0.jar /$PROJECT_NAME/lib/$PROJECT_NAME-1.0.jar
ENTRYPOINT ["/${PROJECT_NAME}/bin/${PROJECT_NAME}"]
EXPOSE 8080
"""
    }

    private void setupProjectUnderTest() {
        writeSettingsFile()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()
        writeJettyMainClass()
    }

    private void writeSettingsFile() {
        temporaryFolder.newFile('settings.gradle') << settingsFile()
    }

    private void writeBasicSetupToBuildFile() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'
            apply plugin: com.bmuschko.gradle.docker.DockerJavaApplicationPlugin

            version = '1.0'
            sourceCompatibility = 1.7

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'org.eclipse.jetty.aggregate:jetty-all:9.2.5.v20141112'
            }

            mainClassName = 'com.bmuschko.gradle.docker.application.JettyMain'
        """
    }

    private void writeCustomTasksToBuildFile() {
        buildFile << imageTasks()
        buildFile << containerTasks()
        buildFile << lifecycleTask()
    }

    private void writeJettyMainClass() {
        File packageDir = temporaryFolder.newFolder('src', 'main', 'java', 'com', 'bmuschko', 'gradle', 'docker', 'application')
        File jettyMainClassFile = new File(packageDir, 'JettyMain.java')
        jettyMainClassFile.text = jettyMainClass()
    }

    private static String jettyMainClass() {
        """
            package com.bmuschko.gradle.docker.application;
            
            import javax.servlet.http.HttpServletRequest;
            import javax.servlet.http.HttpServletResponse;
            import javax.servlet.ServletException;
            
            import java.io.IOException;
            
            import org.eclipse.jetty.server.Server;
            import org.eclipse.jetty.server.Request;
            import org.eclipse.jetty.server.handler.AbstractHandler;
            
            public class JettyMain extends AbstractHandler {
                public void handle(String target,
                                   Request baseRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response)
                        throws IOException, ServletException {
                    response.setContentType("text/html;charset=utf-8");
                    response.setStatus(HttpServletResponse.SC_OK);
                    baseRequest.setHandled(true);
                    response.getWriter().println("Hello, Docker!");
                }
            
                public static void main(String[] args) throws Exception {
                    Server server = new Server(8080);
                    server.setHandler(new JettyMain());
                    server.start();
                    server.join();
                }
            }
        """
    }

    private File dockerFile() {
        new File(projectDir, 'build/docker/Dockerfile')
    }
}
