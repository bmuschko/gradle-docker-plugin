package com.bmuschko.gradle.docker

import spock.lang.IgnoreIf

@IgnoreIf({ !AbstractIntegrationTest.isDockerServerInfoUrlReachable() })
class DockerJavaApplicationPluginIntegrationTest extends ToolingApiIntegrationTest {
    def "Can create image for Java application with default configuration"() {
        createJettMainClass()
        writeBuildFile()

        when:
        GradleInvocationResult result = runTasks('inspectImage')

        then:
        new File(projectDir, 'build/docker/Dockerfile').exists()
        result.output.contains('Author           : ')
    }

    def "Can create image for Java application with user-driven configuration"() {
        createJettMainClass()
        writeBuildFile()

        buildFile << """
docker {
    javaApplication {
        baseImage = 'dockerfile/java:openjdk-7-jre'
        maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
        port = 9090
        tag = 'jettyapp:1.115'
    }
}
"""

        when:
        GradleInvocationResult result = runTasks('inspectImage')

        then:
        new File(projectDir, 'build/docker/Dockerfile').exists()
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
    }

    private void createJettMainClass() {
        File packageDir = createDir(new File(projectDir, 'src/main/java/com/bmuschko/gradle/docker/application'))
        File jettyMainClass = createNewFile(packageDir, 'JettyMain.java')
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

    private void writeBuildFile() {
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

import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

task inspectImage(type: DockerInspectImage) {
    dependsOn dockerBuildImage
    targetImageId { dockerBuildImage.getImageId() }
}
"""
    }
}
