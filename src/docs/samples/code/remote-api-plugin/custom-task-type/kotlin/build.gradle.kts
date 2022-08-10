plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.github.dockerjava.api.model.Image

// tag::task-type-usage[]
val imageIdForName by tasks.creating(DockerImageIdForName::class) {
    filteredImageName.set("alpine:3.4")
}

val printImageId by tasks.creating {
    dependsOn(imageIdForName)
    doLast {
        logger.quiet("Resolved image ID ${imageIdForName.imageId.get()} for name ${imageIdForName.filteredImageName.get()}")
    }
}
// end::task-type-usage[]

val pullImage by tasks.creating(DockerPullImage::class) {
    image.set("alpine:3.4")
}

imageIdForName.dependsOn(pullImage)

// tag::task-type-definition[]
open class DockerImageIdForName : AbstractDockerRemoteApiTask {
    @Input
    val filteredImageName : Property<String> = project.objects.property(String::class)

    @Internal
    val imageId : Property<String> = project.objects.property(String::class)

    constructor() {
        onNext(Action<Image> {
            this.withGroovyBuilder {
                imageId.set(getProperty("id") as String)
            }
        })
    }

    override fun runRemoteCommand() {
        val images = getDockerClient().listImagesCmd()
            .withImageNameFilter(filteredImageName.get())
            .exec()

        for(image in images) {
            nextHandler.execute(image)
        }
    }
}
// end::task-type-definition[]
