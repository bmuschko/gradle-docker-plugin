plugins {
    java
    application
    id("com.bmuschko.docker-java-application") version "{project-version}"
}

version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jetty.aggregate:jetty-all:9.2.5.v20141112")
}

application {
    mainClassName = "com.bmuschko.gradle.docker.application.JettyMain"
}

docker {
    javaApplication {
        maintainer.set("Jon Doe 'jon.doe@gmail.com'")
    }
}