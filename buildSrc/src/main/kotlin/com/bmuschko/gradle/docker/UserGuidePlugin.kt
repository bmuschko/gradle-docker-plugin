package com.bmuschko.gradle.docker

import org.asciidoctor.gradle.AsciidoctorExtension
import org.asciidoctor.gradle.AsciidoctorPlugin
import org.asciidoctor.gradle.AsciidoctorTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.*

class UserGuidePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyAsciidocPlugin()
        configureAsciidoctorExtension()
        configureAsciidoctorTask()
    }

    private
    fun Project.applyAsciidocPlugin() {
        apply<AsciidoctorPlugin>()
    }

    private
    fun Project.configureAsciidoctorExtension() {
        configure<AsciidoctorExtension> {
            setVersion("1.6.2")
        }
    }

    private
    fun Project.configureAsciidoctorTask() {
        tasks.named<AsciidoctorTask>("asciidoctor") {
            sourceDir = file("src/docs/asciidoc")
            sources(delegateClosureOf<PatternSet> {
                include("index.adoc")
            })

            attributes(
                mapOf(
                    "toc" to "left",
                    "source-highlighter" to "prettify",
                    "icons" to "font",
                    "numbered" to "",
                    "idprefix" to "",
                    "docinfo1" to "true",
                    "sectanchors" to "true",
                    "samplesCodeDir" to file("src/docs/samples/code")
                )
            )
        }

        // Required as the project version is lazily-calculated by the gradle-git plugin
        afterEvaluate {
            tasks.named<AsciidoctorTask>("asciidoctor") {
                attributes.set("project-version", project.version.toString())
            }
        }
    }
}