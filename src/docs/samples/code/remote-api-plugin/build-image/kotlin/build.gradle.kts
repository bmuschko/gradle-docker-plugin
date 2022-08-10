plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

val createDockerfile by tasks.creating(Dockerfile::class) {
    from("ubuntu:12.04")
    label(mapOf("maintainer" to "Benjamin Muschko 'benjamin.muschko@gmail.com'"))
}

tasks.create("buildImage", DockerBuildImage::class) {
    dependsOn(createDockerfile)
    images.add("bmuschko/myimage:latest")
}