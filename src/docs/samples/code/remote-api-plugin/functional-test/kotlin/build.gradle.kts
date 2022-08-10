plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.*

val buildMyAppImage by tasks.creating(DockerBuildImage::class) {
    inputDir.set(file("docker/myapp"))
    images.add("test/myapp:latest")
}

val createMyAppContainer by tasks.creating(DockerCreateContainer::class) {
    dependsOn(buildMyAppImage)
    targetImageId(buildMyAppImage.getImageId())
    hostConfig.portBindings.set(listOf("8080:8080"))
    hostConfig.autoRemove.set(true)
}

val startMyAppContainer by tasks.creating(DockerStartContainer::class) {
    dependsOn(createMyAppContainer)
    targetContainerId(createMyAppContainer.getContainerId())
}

val stopMyAppContainer by tasks.creating(DockerStopContainer::class) {
    targetContainerId(createMyAppContainer.getContainerId())
}

tasks.create("functionalTestMyApp", Test::class) {
    dependsOn(startMyAppContainer)
    finalizedBy(stopMyAppContainer)
}