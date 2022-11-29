package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

class TestSetupPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val testRuntimeClasspath by configurations

        sourceSets.create("testSetup") {
            val sourceDirectorySet = extensions.getByType(GroovySourceDirectorySet::class.java)
            sourceDirectorySet.srcDir("src/testSetup/groovy")
            resources.srcDir("src/testSetup/resources")
            compileClasspath += sourceSets["main"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        tasks.withType<Test>().configureEach {
            testLogging {
                events("skipped")
            }

            maxParallelForks = determineMaxParallelForks()
            failFast = true
            useJUnitPlatform()
        }
    }

    private
    fun determineMaxParallelForks(): Int {
        return if ((Runtime.getRuntime().availableProcessors() / 2) < 1) 1 else (Runtime.getRuntime().availableProcessors() / 2)
    }
}