package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerJavaApplicationPluginFunctionalTest extends AbstractFunctionalTest {
    public static final CUSTOM_BASE_IMAGE = 'yaronr/openjdk-7-jre'

    def "Can create image for Java application with default configuration"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

        when:
        BuildResult result = build('startContainer')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM java
MAINTAINER ${System.getProperty('user.name')}
ADD ${projectName}-1.0.tar /
ENTRYPOINT ["/${projectName}-1.0/bin/${projectName}"]
EXPOSE 8080
"""
        result.output.contains('Author           : ')
    }

    def "Can create image for Java application with user-driven configuration"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
                    port = 9090
                    tag = 'jettyapp:1.115'
                }
            }
        """

        when:
        BuildResult result = build('startContainer', '-s')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM $CUSTOM_BASE_IMAGE
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
ADD ${projectName}-1.0.tar /
ENTRYPOINT ["/${projectName}-1.0/bin/${projectName}"]
EXPOSE 9090
"""
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can create image for Java application with user-driven configuration with several ports"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

        buildFile << """
            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
                    ports = [9090, 8080]
                    tag = 'jettyapp:1.115'
                }
            }
        """

        when:
        BuildResult result = build('startContainer', '-s')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
ADD ${projectName}-1.0.tar /
ENTRYPOINT ["/${projectName}-1.0/bin/${projectName}"]
EXPOSE 9090 8080
"""
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    def "Can create image for Java application with additional files"() {
        String projectName = temporaryFolder.root.name
        temporaryFolder.newFile('file1.txt')
        temporaryFolder.newFile('file2.txt')
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

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
                    maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
                    port = 9090
                    tag = 'jettyapp:1.115'
                }
            }
        """

        when:
        BuildResult result = build('startContainer')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
"""FROM $CUSTOM_BASE_IMAGE
MAINTAINER Benjamin Muschko "benjamin.muschko@gmail.com"
ADD ${projectName}-1.0.tar /
ENTRYPOINT ["/${projectName}-1.0/bin/${projectName}"]
EXPOSE 9090
ADD file1.txt /some/dir/file1.txt
ADD file2.txt /other/dir/file2.txt
"""
        new File(projectDir, 'build/docker/file1.txt').exists()
        new File(projectDir, 'build/docker/file2.txt').exists()
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    @Requires({ TestPrecondition.DOCKERHUB_CREDENTIALS_AVAILABLE })
    def "Can create image for Java application and push to DockerHub"() {
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        Properties gradleProperties = TestPrecondition.readDockerHubCredentials()
        new File(projectDir, 'gradle.properties') << """
            dockerHubUsername=${gradleProperties['dockerHubUsername']}
            dockerHubPassword=${gradleProperties['dockerHubPassword']}
            dockerHubEmail=${gradleProperties['dockerHubEmail']}
        """
        buildFile << """
            applicationName = 'javaapp'

            docker {
                registryCredentials {
                    username = project.property('dockerHubUsername')
                    password = project.property('dockerHubPassword')
                    email = project.property('dockerHubEmail')
                }

                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    tag = "\$docker.registryCredentials.username/javaapp"
                }
            }
        """

        when:
        build('dockerPushImage')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
MAINTAINER ${System.getProperty('user.name')}
ADD javaapp-1.0.tar /
ENTRYPOINT ["/javaapp-1.0/bin/javaapp"]
EXPOSE 8080
"""
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to private registry"() {
        createJettyMainClass()
        writeBasicSetupToBuildFile()
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
        build('dockerPushImage')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
MAINTAINER ${System.getProperty('user.name')}
ADD javaapp-1.0.tar /
ENTRYPOINT ["/javaapp-1.0/bin/javaapp"]
EXPOSE 8080
"""
        noExceptionThrown()
    }

    private void createJettyMainClass() {
        File packageDir = temporaryFolder.newFolder('src', 'main', 'java', 'com', 'bmuschko', 'gradle', 'docker', 'application')
        File jettyMainClass = new File(packageDir, 'JettyMain.java')
        jettyMainClass.createNewFile()
        jettyMainClass << """
package com.bmuschko.gradle.docker.application;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class JettyMain extends AbstractHandler
{
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException, ServletException
    {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        response.getWriter().println("<p><img src='http://www.docker.io/static/img/homepage-docker-logo.png'></p>");
        response.getWriter().println("<br><h1>Hello, Docker!</h1>");
    }

    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
        server.setHandler(new JettyMain());

        server.start();
        server.join();
    }
}
"""
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
}
