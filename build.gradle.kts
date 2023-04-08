import java.text.SimpleDateFormat
import java.util.Date

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    groovy
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
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

configurations.shaded {
    exclude("org.slf4j")
}

dependencies {
    shaded(libs.bundles.docker.java)
    shaded(libs.activation)
    shaded(libs.asm)
    testImplementation(libs.spock.core) {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation(libs.zt.zip)
    functionalTestImplementation(libs.commons.vfs2)
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
    tags = listOf("docker", "container", "image", "lightweight", "vm", "linux")
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"

    if (!System.getenv("CI").isNullOrEmpty()) {
        publishAlways()
        tag("CI")
    }
}
