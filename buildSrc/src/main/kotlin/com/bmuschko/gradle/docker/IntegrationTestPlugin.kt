package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

class IntegrationTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.the<JavaPluginConvention>().sourceSets
        val testRuntimeClasspath by configurations

        val integrationTestSourceSet by sourceSets.creating {
            withConvention(GroovySourceSet::class) {
                groovy.srcDir("src/integTest/groovy")
            }
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
            reports {
                html.destination = file("${html.destination}/integration")
                junitXml.destination = file("${junitXml.destination}/integration")
            }
        }

        tasks["check"].dependsOn(integrationTest)
    }
}