package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.*

class AdditionalArtifactsPlugin: Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSets = the<SourceSetContainer>()
        val groovydoc: Groovydoc by tasks
        val javadoc: Javadoc by tasks

        tasks.create("sourcesJar", Jar::class) {
            archiveClassifier.set("sources")
            from(sourceSets["main"].allSource)
        }

        tasks.create("groovydocJar", Jar::class) {
            dependsOn(groovydoc)
            archiveClassifier.set("groovydoc")
            from(groovydoc.destinationDir)
        }

        tasks.create("javadocJar", Jar::class) {
            dependsOn(javadoc)
            archiveClassifier.set("javadoc")
            from(javadoc.destinationDir)
        }
    }
}