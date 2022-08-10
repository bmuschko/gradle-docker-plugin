import com.bmuschko.gradle.docker.tasks.image.Dockerfile

// tag::plugins[]
plugins {
    java
    id("com.bmuschko.docker-java-application") version "{gradle-project-version}"
}
// end::plugins[]

// tag::extension[]
docker {
    javaApplication {
        baseImage.set("dockerfile/java:openjdk-7-jre")
        maintainer.set("Benjamin Muschko 'benjamin.muschko@gmail.com'")
        ports.set(listOf(9090, 5701))
        images.set(setOf("jettyapp:1.115", "jettyapp:latest"))
        jvmArgs.set(listOf("-Xms256m", "-Xmx2048m"))
    }
}
// end::extension[]

// tag::dockerfile-addition-instructions[]
tasks.named<Dockerfile>("dockerCreateDockerfile") {
    instruction("RUN ls -la")
    environmentVariable("JAVA_OPTS", "-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap")
}
// end::dockerfile-addition-instructions[]

// tag::instruction-template[]
tasks.named<Dockerfile>("dockerCreateDockerfile") {
    instructionsFromTemplate(file("Dockerfile.tmpl"))
}
// end::instruction-template[]