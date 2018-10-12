import java.text.SimpleDateFormat
import java.util.Date

plugins {
    groovy
    `java-gradle-plugin`
    id("com.bmuschko.gradle.docker.test-setup")
    id("com.bmuschko.gradle.docker.integration-test")
    id("com.bmuschko.gradle.docker.functional-test")
    id("com.bmuschko.gradle.docker.doc-test")
    id("com.bmuschko.gradle.docker.additional-artifacts")
    id("com.bmuschko.gradle.docker.static-code-analysis")
    id("com.bmuschko.gradle.docker.user-guide")
    id("com.bmuschko.gradle.docker.documentation")
    id("com.bmuschko.gradle.docker.publishing")
    id("com.bmuschko.gradle.docker.release")
}

group = "com.bmuschko"

repositories {
    jcenter()
}

dependencies {
    testImplementation("org.spockframework:spock-core:1.2-groovy-2.4") {
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