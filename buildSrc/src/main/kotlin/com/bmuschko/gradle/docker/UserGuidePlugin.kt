package com.bmuschko.gradle.docker

import org.asciidoctor.gradle.AsciidoctorPlugin
import org.asciidoctor.gradle.AsciidoctorTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.named

class UserGuidePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyAsciidocPlugin()
        configureAsciidoctorTask()
    }

    private
    fun Project.applyAsciidocPlugin() {
        apply<AsciidoctorPlugin>()
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
                    "source-highlighter" to "coderay",
                    "icons" to "font",
                    "numbered" to "",
                    "idprefix" to "",
                    "docinfo1" to "true",
                    "sectanchors" to "true",
                    "samplesCodeDir" to file("src/docs/samples/code")
                )
            )
        }
    }
}