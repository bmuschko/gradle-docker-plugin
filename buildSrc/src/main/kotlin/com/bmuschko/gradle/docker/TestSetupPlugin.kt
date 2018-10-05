package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

class TestSetupPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.the<JavaPluginConvention>().sourceSets
        val testRuntimeClasspath by configurations

        sourceSets.create("testSetup") {
            withConvention(GroovySourceSet::class) {
                groovy.srcDir("src/testSetup/groovy")
            }
            resources.srcDir("src/testSetup/resources")
            compileClasspath += sourceSets["main"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        tasks.withType<Test>().configureEach {
            testLogging {
                events("skipped")
            }
        }
    }
}