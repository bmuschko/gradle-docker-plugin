plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.*

val buildMyAppImage by tasks.creating(DockerBuildImage::class) {
    inputDir.set(file("docker/myapp"))
    images.add("test/myapp")
}

val createDBContainer by tasks.creating(DockerCreateContainer::class) {
    targetImageId("postgres:latest")
    containerName.set("docker_auto")
    hostConfig.autoRemove.set(true)
}

val createMyAppContainer by tasks.creating(DockerCreateContainer::class) {
    dependsOn(buildMyAppImage, createDBContainer)
    targetImageId(buildMyAppImage.getImageId())
    hostConfig.portBindings.set(listOf("8080:8080"))
    hostConfig.autoRemove.set(true)
    hostConfig.links.set(listOf("docker_auto:database"))

    // If you use Systemd in containers you should also add lines. #320
    hostConfig.binds.set(mapOf("/sys/fs/cgroup" to "/sys/fs/cgroup"))
    tty.set(true)
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