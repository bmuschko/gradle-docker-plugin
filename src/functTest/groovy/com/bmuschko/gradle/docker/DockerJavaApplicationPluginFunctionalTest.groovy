package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

class DockerJavaApplicationPluginFunctionalTest extends AbstractFunctionalTest {
    public static final DEFAULT_BASE_IMAGE = 'openjdk:jre-alpine'
    public static final CUSTOM_BASE_IMAGE = 'openjdk:8u171-jre-alpine'

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
"""FROM $DEFAULT_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
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
                    maintainer = 'benjamin.muschko@gmail.com'
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
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
EXPOSE 9090
"""
        result.output.contains('Author           : ')
        result.output.contains('Labels           : [maintainer:benjamin.muschko@gmail.com]')
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
                    maintainer = 'benjamin.muschko@gmail.com'
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
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
EXPOSE 9090 8080
"""
        result.output.contains('Author           : ')
        result.output.contains('Labels           : [maintainer:benjamin.muschko@gmail.com]')
    }

    def "Can create image for Java application with user-driven configuration without exposed ports"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

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
        BuildResult result = build('startContainer', '-s')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
            """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
"""
        result.output.contains('Author           : ')
        result.output.contains('Labels           : [maintainer:benjamin.muschko@gmail.com]')
    }

    def "Can create image for Java application with user-driven configuration with custom cmd/entrypoint"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

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
        build('dockerBuildImage')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
CMD ["arg1"]
ENTRYPOINT ["bin/run.sh"]
EXPOSE 9090
"""
    }

    def "Can create image for Java application with user-driven configuration with no cmd/entrypoint"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

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
        build('dockerBuildImage')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar

EXPOSE 9090
"""

        where:
        execDirective << ['exec null',
                          'exec {}']
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
                    maintainer = 'benjamin.muschko@gmail.com'
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
LABEL maintainer=benjamin.muschko@gmail.com
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
ADD file1.txt /some/dir/file1.txt
ADD file2.txt /other/dir/file2.txt
EXPOSE 9090
"""
        new File(projectDir, 'build/docker/file1.txt').exists()
        new File(projectDir, 'build/docker/file2.txt').exists()
        result.output.contains('Author           : ')
        result.output.contains('Labels           : [maintainer:benjamin.muschko@gmail.com]')
    }

    @Requires({ TestPrecondition.DOCKER_HUB_CREDENTIALS_AVAILABLE })
    def "Can create image for Java application and push to DockerHub"() {
        String projectName = temporaryFolder.root.name
        createJettyMainClass()
        writeBasicSetupToBuildFile()
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
        build('dockerPushImage')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
                """FROM $CUSTOM_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD javaapp /javaapp
ADD app-lib/${projectName}-1.0.jar /javaapp/lib/$projectName-1.0.jar
ENTRYPOINT ["/javaapp/bin/javaapp"]
EXPOSE 8080
"""
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "Can create image for Java application and push to private registry"() {
        String projectName = temporaryFolder.root.name
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
LABEL maintainer=${System.getProperty('user.name')}
ADD javaapp /javaapp
ADD app-lib/${projectName}-1.0.jar /javaapp/lib/$projectName-1.0.jar
ENTRYPOINT ["/javaapp/bin/javaapp"]
EXPOSE 8080
"""
    }

    def "Can create image without MAINTAINER"() {
        String projectName = temporaryFolder.root.name
        writeBasicSetupToBuildFile()
        writeCustomTasksToBuildFile()

        buildFile << """
            docker {
                javaApplication {
                    skipMaintainer = true
                }
            }
        """

        when:
        build('inspectImage', '-s')

        then:
        File dockerfile = new File(projectDir, 'build/docker/Dockerfile')
        dockerfile.exists()
        dockerfile.text ==
            """FROM $DEFAULT_BASE_IMAGE
LABEL maintainer=${System.getProperty('user.name')}
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
EXPOSE 8080
"""
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
