plugins {
    groovy
    `java-gradle-plugin`
    id("org.ajoberstar.grgit") version "1.7.1"
    id("com.bmuschko.gradle.docker.test-setup")
    id("com.bmuschko.gradle.docker.integration-test")
    id("com.bmuschko.gradle.docker.functional-test")
    id("com.bmuschko.gradle.docker.additional-artifacts")
}
//apply(plugin = "groovy")
//apply(plugin = "java-gradle-plugin")
//apply(plugin = "org.ajoberstar.release-opinion")
//apply from: "$rootDir/gradle/test-setup.gradle"
//apply from: "$rootDir/gradle/integration-test.gradle"
//apply from: "$rootDir/gradle/functional-test.gradle"
//apply from: "$rootDir/gradle/additional-artifacts.gradle"
//apply from: "$rootDir/gradle/release.gradle"
//apply from: "$rootDir/gradle/publishing.gradle"
//apply from: "$rootDir/gradle/asciidoc.gradle"
//apply from: "$rootDir/gradle/documentation.gradle"
//apply from: "$rootDir/gradle/codenarc.gradle"

group = "com.bmuschko"

repositories {
    jcenter()
}

dependencies {
    "testImplementation"("org.spockframework:spock-core:1.1-groovy-2.4") {
        exclude(group = "org.codehaus.groovy")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_6
    targetCompatibility = JavaVersion.VERSION_1_6
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Implementation-Title"] = "Gradle Docker plugin"
        attributes["Implementation-Version"] = project.version
        attributes["Built-By"] = System.getProperty("user.name")
        attributes["Built-Date"] = ""
        attributes["Built-JDK"] = System.getProperty("java.version")
        attributes["Built-Gradle"] = gradle.gradleVersion
    }
}