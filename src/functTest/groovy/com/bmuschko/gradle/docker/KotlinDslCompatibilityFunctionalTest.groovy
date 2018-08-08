package com.bmuschko.gradle.docker

import static com.bmuschko.gradle.docker.fixtures.DockerConventionPluginFixture.CUSTOM_BASE_IMAGE
import static com.bmuschko.gradle.docker.fixtures.DockerJavaApplicationPluginFixture.writeJettyMainClass
import static com.bmuschko.gradle.docker.fixtures.DockerSpringBootApplicationPluginFixture.writeSpringBootApplicationClasses

class KotlinDslCompatibilityFunctionalTest extends AbstractKotlinDslFunctionalTest {

    def "can configure extension for Docker Java application plugin"() {
        given:
        buildFile << javaApplicationBuildFile()
        buildFile << """
            docker {
                javaApplication {
                    baseImage = "$CUSTOM_BASE_IMAGE"
                }
            }
        """
        writeJettyMainClass(projectDir)

        when:
        build('help')

        then:
        noExceptionThrown()
    }

    def "can configure extension for Docker Spring Boot application plugin"() {
        given:
        buildFile << springBootApplicationBuildFile()
        buildFile << """
            docker {
                springBootApplication {
                    baseImage = "$CUSTOM_BASE_IMAGE"
                }
            }
        """
        writeSpringBootApplicationClasses(projectDir)

        when:
        build('help')

        then:
        noExceptionThrown()
    }

    private static String javaApplicationBuildFile() {
        """
            plugins {
                java
                application
                id("com.bmuschko.docker-java-application")
            }

            ${jcenterRepository()}
            
            dependencies {
                implementation("org.eclipse.jetty.aggregate:jetty-all:9.2.5.v20141112")
            }

            application {
                mainClassName = "com.bmuschko.gradle.docker.application.JettyMain"
            }
        """
    }

    private static String springBootApplicationBuildFile() {
        """
            plugins {
                id("org.springframework.boot") version "2.0.3.RELEASE"
                id("io.spring.dependency-management") version "1.0.5.RELEASE"
                war
                id("com.bmuschko.docker-spring-boot-application")
            }

            ${jcenterRepository()}
            
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-web")
            }
        """
    }

    private static String jcenterRepository() {
        """
            repositories {
                jcenter()
            }
        """
    }
}
