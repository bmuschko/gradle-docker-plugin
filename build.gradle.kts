import java.text.SimpleDateFormat
import java.util.Date

plugins {
    groovy
    `java-gradle-plugin`
    `build-scan`
    com.bmuschko.gradle.docker.`test-setup`
    com.bmuschko.gradle.docker.`integration-test`
    com.bmuschko.gradle.docker.`functional-test`
    com.bmuschko.gradle.docker.`doc-test`
    com.bmuschko.gradle.docker.`additional-artifacts`
    com.bmuschko.gradle.docker.`shaded-artifacts`
    com.bmuschko.gradle.docker.`user-guide`
    com.bmuschko.gradle.docker.documentation
    com.bmuschko.gradle.docker.publishing
    com.bmuschko.gradle.docker.release
}

group = "com.bmuschko"

repositories {
    jcenter()
}

dependencies {
    shaded("com.github.docker-java:docker-java:3.2.0")
    shaded("javax.activation:activation:1.1.1")
    shaded("org.ow2.asm:asm:7.3.1")
    testImplementation("org.spockframework:spock-core:1.2-groovy-2.5") {
        exclude(group = "org.codehaus.groovy")
    }
    testImplementation("org.zeroturnaround:zt-zip:1.13")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
            implementationClass = "com.bmuschko.gradle.docker.DockerRemoteApiPlugin"
        }
        create("docker-java-application") {
            id = "com.bmuschko.docker-java-application"
            implementationClass = "com.bmuschko.gradle.docker.DockerJavaApplicationPlugin"
        }
        create("docker-spring-boot-application") {
            id = "com.bmuschko.docker-spring-boot-application"
            implementationClass = "com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin"
        }
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
