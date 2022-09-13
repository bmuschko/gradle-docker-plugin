package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.registering

class SmokeTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val smokeTest by project.tasks.registering(SmokeTestTask::class) {
            smokeTestIncludes.set(project.providers.gradleProperty("smokeTestIncludes"))
        }

        project.tasks.named("check") {
            dependsOn(smokeTest)
        }
    }
}
