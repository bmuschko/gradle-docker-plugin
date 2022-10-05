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
import org.gradle.util.GradleVersion

class FunctionalTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val testRuntimeClasspath by configurations

        val functionalTestSourceSet = sourceSets.create("functionalTest") {
            val sourceDirectorySet = extensions.getByType(GroovySourceDirectorySet::class.java)
            sourceDirectorySet.srcDir("src/functTest/groovy")
            resources.srcDir("src/functTest/resources")
            compileClasspath += sourceSets["main"]!!.output + sourceSets["testSetup"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        val functionalTest by tasks.creating(Test::class) {
            description = "Runs the functional tests"
            group = "verification"
            testClassesDirs = functionalTestSourceSet.output.classesDirs
            classpath = functionalTestSourceSet.runtimeClasspath
            mustRunAfter("test", "integrationTest")
            testLogging {
                showStandardStreams = true
                events("started", "passed", "failed")
            }
            environment("CURRENT_GRADLE_VERSION", GradleVersion.current().version)
        }

        tasks["check"].dependsOn(functionalTest)
    }
}
