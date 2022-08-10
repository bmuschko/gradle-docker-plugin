plugins {
    war
    id("org.springframework.boot") version "2.0.3.RELEASE"
    id("io.spring.dependency-management") version "1.0.5.RELEASE"
    id("com.bmuschko.docker-spring-boot-application") version "{gradle-project-version}"
}

version = "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat")
    providedRuntime("org.apache.tomcat.embed:tomcat-embed-jasper")
}

docker {
    springBootApplication {
        baseImage.set("openjdk:8-alpine")
    }
}