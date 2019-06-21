// tag::plugins[]
plugins {
    java
    id("org.springframework.boot") version "2.0.3.RELEASE"
    id("com.bmuschko.docker-spring-boot-application") version "{project-version}"
}
// end::plugins[]

// tag::extension[]
docker {
    springBootApplication {
        baseImage.set("openjdk:8-alpine")
        ports.set(listOf(9090, 8080))
        tags.set(listOf("awesome-spring-boot:1.115"))
        jvmArgs.set(listOf("-Dspring.profiles.active=production", "-Xmx2048m"))
    }
}
// end::extension[]