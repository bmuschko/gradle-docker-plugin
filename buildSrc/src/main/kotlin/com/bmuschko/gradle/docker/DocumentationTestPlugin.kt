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

class DocumentationTestPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val testRuntimeClasspath by configurations

        val docTestSourceSet = sourceSets.create("docTest") {
            val sourceDirectorySet = extensions.getByType(GroovySourceDirectorySet::class.java)
            sourceDirectorySet.srcDir("src/docTest/groovy")
            resources.srcDir("src/docTest/resources")
            compileClasspath += sourceSets["main"]!!.output + sourceSets["testSetup"]!!.output + testRuntimeClasspath
            runtimeClasspath += output + compileClasspath
        }

        val docTest by tasks.creating(Test::class) {
            description = "Runs the documentation tests"
            group = "verification"
            testClassesDirs = docTestSourceSet.output.classesDirs
            classpath = docTestSourceSet.runtimeClasspath
            inputs.dir(file("src/docs/samples"))
            mustRunAfter("test", "integrationTest", "functionalTest")
            systemProperty("samples.code.dir", file("src/docs/samples"))
        }

        tasks["check"].dependsOn(docTest)
    }
}