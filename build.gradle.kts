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
    testImplementation("org.spockframework:spock-core:1.2-groovy-2.5") {
        exclude(group = "org.codehaus.groovy")
    }
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

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"

    if (!System.getenv("CI").isNullOrEmpty()) {
        publishAlways()
        tag("CI")
    }
}