plugins {
    id("com.bmuschko.docker-remote-api") version "{project-version}"
}

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

val createDockerfile = tasks.creating(Dockerfile::class) {
    destFile.set(file("build/mydockerfile/Dockerfile"))
    from("ubuntu:12.04")
    label(mapOf("maintainer" to "Benjamin Muschko 'benjamin.muschko@gmail.com'"))
}

val destFile = createDockerfile.getDestFile()
val dockerfileDir = destFile.get().asFile.parentFile

tasks.create("buildImage", DockerBuildImage::class) {
    dependsOn(createDockerfile)
    inputDir.set(dockerfileDir)
    tag.set("bmuschko/myimage:latest")
}