package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

class FunctionalTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.the<JavaPluginConvention>().sourceSets
        val testRuntimeClasspath by configurations

        val functionalTestSourceSet = sourceSets.create("functionalTest") {
            withConvention(GroovySourceSet::class) {
                groovy.srcDir("src/functTest/groovy")
            }
            resources.srcDir("src/functTest/resources")
            compileClasspath += sourceSets["main"]!!.output + sourceSets["testSetup"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        val functionalTest by tasks.creating(Test::class) {
            description = "Runs the functional tests"
            group = "verification"
            inputs.property("harborUsername", System.getenv()["HARBOR_USERNAME"])
            inputs.property("harborPassword", System.getenv()["HARBOR_PASSWORD"])
            testClassesDirs = functionalTestSourceSet.output.classesDirs
            classpath = functionalTestSourceSet.runtimeClasspath
            mustRunAfter("test", "integrationTest")
            testLogging {
                showStandardStreams = true
                events("started", "passed", "failed")
            }
            if (System.getenv().containsKey("HARBOR_USERNAME")) {
                environment("HARBOR_USERNAME", System.getenv()["HARBOR_USERNAME"].toString())
            }
            if (System.getenv().containsKey("HARBOR_PASSWORD")) {
                environment("HARBOR_PASSWORD", System.getenv()["HARBOR_PASSWORD"].toString())
            }
        }

        tasks["check"].dependsOn(functionalTest)
    }
}
