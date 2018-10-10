plugins {
    id("com.bmuschko.docker-remote-api") version "{project-version}"
}

import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.*

val buildMyAppImage = tasks.creating(DockerBuildImage::class) {
    inputDir.set(file("docker/myapp"))
    tag.set("test/myapp:latest")
}

val createMyAppContainer = tasks.creating(DockerCreateContainer::class) {
    dependsOn(buildMyAppImage)
    targetImageId(buildMyAppImage.getImageId())
    portBindings.set(listOf("8080:8080"))
}

val startMyAppContainer = tasks.creating(DockerStartContainer::class) {
    dependsOn(createMyAppContainer)
    targetContainerId(createMyAppContainer.getContainerId())
}

val stopMyAppContainer = tasks.create(DockerStopContainer::class) {
    targetContainerId(createMyAppContainer.getContainerId())
}

tasks.create("functionalTestMyApp", Test::class) {
    dependsOn(startMyAppContainer)
    finalizedBy(stopMyAppContainer)
}