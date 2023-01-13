package com.bmuschko.gradle.docker

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*

class ShadedArtifactsPlugin: Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyShadowPlugin()
        val shaded = createShadedConfiguration()
        val shadowJar = configureShadowJarTask(shaded)
        wireShadowJarTaskInLifecycle(shadowJar)
    }

    private
    fun Project.applyShadowPlugin() {
        apply<ShadowPlugin>()
    }

    private
    fun Project.createShadedConfiguration(): Configuration {
        val shaded by configurations.creating
        val implementation = configurations.getByName("implementation")
        implementation.extendsFrom(shaded)
        return shaded
    }

    private
    fun Project.configureShadowJarTask(shaded: Configuration): TaskProvider<ShadowJar> {
        val packagesToRelocate = listOf(
                "javassist",
                "org.glassfish",
                "org.jvnet",
                "jersey.repackaged",
                "com.fasterxml",
                "io.netty",
                "org.bouncycastle",
                "org.apache",
                "org.aopalliance",
                "org.scijava",
                "com.google",
                "javax.annotation",
                "javax.ws",
                "net.sf",
                "org.objectweb"
        )
        return tasks.named<ShadowJar>("shadowJar") {
            archiveClassifier.set(null)
            configurations = listOf(shaded)
            mergeServiceFiles()
            for (pkg in packagesToRelocate) {
                relocate(pkg, "com.bmuschko.gradle.docker.shaded.$pkg")
            }
        }
    }

    private
    fun Project.wireShadowJarTaskInLifecycle(shadowJar: TaskProvider<ShadowJar>) {
        val jar: Jar by tasks
        jar.enabled = false
        tasks["assemble"].dependsOn(shadowJar)
    }
}
