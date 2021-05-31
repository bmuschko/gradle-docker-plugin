import java.text.SimpleDateFormat
import java.util.Date

plugins {
    groovy
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.14.0"
    `build-scan`
    com.bmuschko.gradle.docker.`test-setup`
    com.bmuschko.gradle.docker.`integration-test`
    com.bmuschko.gradle.docker.`functional-test`
    com.bmuschko.gradle.docker.`doc-test`
    com.bmuschko.gradle.docker.`additional-artifacts`
    com.bmuschko.gradle.docker.`shaded-artifacts`
    com.bmuschko.gradle.docker.`user-guide`
    com.bmuschko.gradle.docker.documentation
    com.bmuschko.gradle.docker.release
}

group = "com.bmuschko"

repositories {
    mavenCentral()
}

dependencies {
    shaded("com.github.docker-java:docker-java-core:3.2.8")
    shaded("com.github.docker-java:docker-java-api:3.2.8")
    shaded("com.github.docker-java:docker-java-transport-httpclient5:3.2.8")
    shaded("javax.activation:activation:1.1.1")
    shaded("org.ow2.asm:asm:9.1")
    testImplementation("org.spockframework:spock-core:1.2-groovy-2.5") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.zeroturnaround:zt-zip:1.13")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Implementation-Title"] = "Gradle Docker plugin"
        attributes["Implementation-Version"] = project.version
        attributes["Built-By"] = System.getProperty("user.name")
        attributes["Built-Date"] = SimpleDateFormat("MM/dd/yyyy").format(Date())
        attributes["Built-JDK"] = System.getProperty("java.version")
        attributes["Built-Gradle"] = gradle.gradleVersion
    }
}

gradlePlugin {
    plugins {
        create("docker-remote-api") {
            id = "com.bmuschko.docker-remote-api"
            displayName = "Gradle Docker Remote API Plugin"
            description = "Plugin that provides tasks for interacting with Docker via its remote API."
            implementationClass = "com.bmuschko.gradle.docker.DockerRemoteApiPlugin"
        }
        create("docker-java-application") {
            id = "com.bmuschko.docker-java-application"
            displayName = "Gradle Docker Java Application Plugin"
            description = "Plugin that provides conventions for building and publishing Docker images for Java applications."
            implementationClass = "com.bmuschko.gradle.docker.DockerJavaApplicationPlugin"
        }
        create("docker-spring-boot-application") {
            id = "com.bmuschko.docker-spring-boot-application"
            displayName = "Gradle Docker Spring Boot Application Plugin"
            description = "Plugin that provides conventions for building and publishing Docker images for Spring Boot applications."
            implementationClass = "com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/bmuschko/gradle-docker-plugin"
    vcsUrl = "https://github.com/bmuschko/gradle-docker-plugin"
    tags = listOf("gradle", "docker", "container", "image", "lightweight", "vm", "linux")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()
    }
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"

    if (!System.getenv("CI").isNullOrEmpty()) {
        publishAlways()
        tag("CI")
    }
}
