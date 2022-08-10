plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer

// tag::on-error[]
tasks.create("removeContainer1", DockerRemoveContainer::class) {
    targetContainerId("container-that-does-not-exist")
    onError {
        // Ignore exception if container does not exist otherwise throw it
        if (!this.message!!.contains("No such container"))
            throw this
    }
}
// end::on-error[]

// tag::on-next[]
tasks.create("logContainer", DockerLogsContainer::class) {
    targetContainerId("container-that-does-not-exist")
    follow.set(true)
    tailAll.set(true)
    onNext {
        // Each log message from the container will be passed as it's made available
        logger.quiet(this.toString())
    }
}
// end::on-next[]

// tag::on-complete[]
tasks.create("removeContainer2", DockerRemoveContainer::class) {
    targetContainerId("container-that-does-exist")
    onComplete {
        println("Executes first")
    }
    doLast {
        println("Executes second")
    }
}
// end::on-complete[]