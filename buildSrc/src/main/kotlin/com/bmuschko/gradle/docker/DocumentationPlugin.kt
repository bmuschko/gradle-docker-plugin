package com.bmuschko.gradle.docker

import org.ajoberstar.gradle.git.publish.GitPublishExtension
import org.ajoberstar.gradle.git.publish.GitPublishPlugin
import org.asciidoctor.gradle.AsciidoctorTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.*

class DocumentationPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyGitPublishPlugin()
        addLinks()
        configureGitPublishExtension()
        configureTaskDependencies()
    }

    private
    fun Project.applyGitPublishPlugin() {
        apply<GitPublishPlugin>()
    }

    private
    fun Project.addLinks() {
        val javaApiUrl = "https://docs.oracle.com/javase/8/docs/api/"
        val groovyApiUrl = "http://docs.groovy-lang.org/2.5.4/html/gapi/"
        val gradleApiUrl = "https://docs.gradle.org/${project.gradle.gradleVersion}/javadoc/"

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).links(javaApiUrl, groovyApiUrl, gradleApiUrl)
        }

        tasks.withType<Groovydoc>().configureEach {
            link(javaApiUrl, "java", "org.xml", "javax", "org.xml")
            link(groovyApiUrl, "groovy", "org.codehaus.groovy")
            link(gradleApiUrl, "org.gradle")
        }
    }

    private
    fun Project.configureGitPublishExtension() {
        val groovydoc: Groovydoc by tasks
        val asciidoctor: AsciidoctorTask by tasks

        configure<GitPublishExtension> {
            repoUri = "https://github.com/bmuschko/gradle-docker-plugin.git"
            branch = "gh-pages"

            contents {
                from(groovydoc) {
                    into("api")
                }
                from("${asciidoctor.outputDir}/html5")
            }
        }
    }

    private
    fun Project.configureTaskDependencies() {
        val groovydoc: Groovydoc by tasks
        val asciidoctor: AsciidoctorTask by tasks
        tasks["gitPublishCopy"].dependsOn(groovydoc, asciidoctor)
    }
}