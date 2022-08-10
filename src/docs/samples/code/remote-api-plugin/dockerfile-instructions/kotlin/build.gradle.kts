plugins {
    id("com.bmuschko.docker-remote-api") version "{gradle-project-version}"
}

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.tasks.image.Dockerfile.FromInstruction
import com.bmuschko.gradle.docker.tasks.image.Dockerfile.From

// tag::existing-instructions[]
tasks.create("createDockerfile", Dockerfile::class) {
    from("openjdk:jre-alpine")
    copyFile("my-app-1.0.jar", "/app/my-app-1.0.jar")
    entryPoint("java")
    defaultCommand("-jar", "/app/my-app-1.0.jar")
    exposePort(8080)
}
// end::existing-instructions[]

// tag::modify-instruction[]
tasks {
    "createDockerfile"(Dockerfile::class) {
        val originalInstructions = instructions.get().toMutableList()
        val fromInstructionIndex = originalInstructions
                .indexOfFirst { item -> item.keyword == FromInstruction.KEYWORD }
        originalInstructions.removeAt(fromInstructionIndex)
        val baseImage = FromInstruction(From("openjdk:8-alpine"))
        originalInstructions.add(0, baseImage)
        instructions.set(originalInstructions)
    }
}
// end::modify-instruction[]

// tag::add-instruction[]
tasks {
    "createDockerfile"(Dockerfile::class) {
        instruction("HEALTHCHECK CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1")
    }
}
// end::add-instruction[]

tasks.create("printDockerfileInstructions") {
    doLast {
        val createDockerfile: Dockerfile by tasks
        val instructions = createDockerfile.instructions.get()
        val joinedInstructions = instructions.map { it.text }
                .joinToString(separator = System.getProperty("line.separator"))
        println(joinedInstructions)
    }
}
