package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult

class DockerSpringBootJavaApplicationPluginFunctionalTest extends AbstractFunctionalTest {
    public static final CUSTOM_BASE_IMAGE = 'yaronr/openjdk-7-jre'

    def setup() {
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
        BuildResult result = build('waitContainer')

        then:
        result.output.contains('Hello world from Spring Boot!')
    }

    private void createSpringMainClass() {
        File packageDir = temporaryFolder.newFolder('src', 'main', 'java', 'com', 'example', 'demo')
        File springMainClass = new File(packageDir, 'DemoApplication.java')
        springMainClass.createNewFile()
        springMainClass << """
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    
    @Override
    public void run(String... args) {
        System.out.println("Hello world from Spring Boot!");
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
            }
        """
    }

    private void writeCustomTasksToBuildFile() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.*
            import com.bmuschko.gradle.docker.tasks.container.*

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
            
            task logContainer(type: DockerLogsContainer) {
                dependsOn startContainer
                targetContainerId { createContainer.getContainerId() }
                follow = true
                tailAll = true
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId { createContainer.getContainerId() }
            }
            
            task waitContainer(type: DockerWaitContainer) {
                dependsOn logContainer
                finalizedBy removeContainer
                targetContainerId { createContainer.getContainerId() }
                doLast{
                    if(getExitCode()) {
                        throw new GradleException("Spring Boot container failed!")
                    }
                }
            }
        """
    }

}
