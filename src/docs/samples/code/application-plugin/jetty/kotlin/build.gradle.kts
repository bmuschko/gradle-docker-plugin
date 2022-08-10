plugins {
    java
    id("com.bmuschko.docker-java-application") version "{gradle-project-version}"
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
    implementation("org.eclipse.jetty.aggregate:jetty-all:9.4.29.v20200521")
}

docker {
    javaApplication {
        maintainer.set("Jon Doe 'jon.doe@gmail.com'")
    }
}