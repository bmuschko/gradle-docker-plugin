package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.*

class AdditionalArtifactsPlugin: Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = project.the<JavaPluginConvention>().sourceSets
        val groovydoc: Groovydoc by tasks.getting
        val javadoc: Javadoc by tasks.getting

        tasks.create("sourcesJar", Jar::class) {
            classifier = "sources"
            from(sourceSets["main"].allSource)
        }

        tasks.create("groovydocJar", Jar::class) {
            dependsOn(groovydoc)
            classifier = "groovydoc"
            from(groovydoc.destinationDir)
        }

        tasks.create("javadocJar", Jar::class) {
            dependsOn(javadoc)
            classifier = "javadoc"
            from(javadoc.destinationDir)
        }
    }
}