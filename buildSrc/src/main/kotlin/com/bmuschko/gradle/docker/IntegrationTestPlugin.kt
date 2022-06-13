package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.provideDelegate

class IntegrationTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val testRuntimeClasspath by configurations

        val integrationTestSourceSet = sourceSets.create("integrationTest") {
            val sourceDirectorySet = extensions.getByType(GroovySourceDirectorySet::class.java)
            sourceDirectorySet.srcDir("src/integTest/groovy")
            resources.srcDir("src/integTest/resources")
            compileClasspath += sourceSets["main"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        val integrationTest by tasks.creating(Test::class) {
            description = "Runs the integration tests"
            group = "verification"
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = integrationTestSourceSet.runtimeClasspath
            mustRunAfter("test")
        }

        tasks["check"].dependsOn(integrationTest)
    }
}