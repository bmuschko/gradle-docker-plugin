package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerSpringBootJavaApplicationPluginFunctionalTest extends AbstractFunctionalTest {
    public static final CUSTOM_BASE_IMAGE = 'yaronr/openjdk-7-jre'

    protected void setupBuildfile() {
        if (buildFile) {
            buildFile.delete()
        }
        buildFile = temporaryFolder.newFile('build.gradle')


        buildFile << """
            plugins {
                id 'com.bmuschko.docker-remote-api'
                id "org.springframework.boot" version "1.5.9.RELEASE"
            }

            repositories {
                mavenCentral()
            }
        """

        setupDockerServerUrl()
        setupDockerCertPath()
        setupDockerPrivateRegistryUrl()

        buildFile << """
            task dockerVersion(type: com.bmuschko.gradle.docker.tasks.DockerVersion)
        """

    }

    def "Can create image for Spring Boot application with custom install and tar tasks"() {
        String projectName = temporaryFolder.root.name
        createSpringMainClass()
        writeBasicSpringBootSetupToBuildFile()
        writeCustomTasksToBuildFile()

        buildFile << """

            docker {
                javaApplication {
                    baseImage = '$CUSTOM_BASE_IMAGE'
                    maintainer = 'Benjamin Muschko "benjamin.muschko@gmail.com"'
                    port = 9090
                    jarTaskName = 'bootJar'
                    installTaskName = 'installBootDist'
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
ADD ${projectName} /${projectName}
ADD app-lib/${projectName}-1.0.jar /$projectName/lib/$projectName-1.0.jar
ENTRYPOINT ["/${projectName}/bin/${projectName}"]
EXPOSE 9090
"""
        result.output.contains('Author           : Benjamin Muschko "benjamin.muschko@gmail.com"')
        result.output.contains(':bootJar')
    }



    private void createSpringMainClass() {
        File packageDir = temporaryFolder.newFolder('src', 'main', 'java', 'com', 'example', 'demo')
        File springMainClass = new File(packageDir, 'DemoApplication.java')
        springMainClass.createNewFile()
        springMainClass << """
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}

"""
    }

    private void writeBasicSpringBootSetupToBuildFile() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'
            apply plugin: com.bmuschko.gradle.docker.DockerJavaApplicationPlugin
            apply plugin: 'org.springframework.boot'

            version = '1.0'
            sourceCompatibility = 1.7

            repositories {
                mavenCentral()
            }

            mainClassName = 'com.example.demo.DemoApplication'
            
            group = 'com.example'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile('org.springframework.boot:spring-boot-starter')
                testCompile('org.springframework.boot:spring-boot-starter-test')
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

}
