package com.bmuschko.gradle.docker

import org.asciidoctor.gradle.jvm.AsciidoctorJPlugin
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named

class UserGuidePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyAsciidocPlugin()
        configureAsciidoctorTask()
    }

    private
    fun Project.applyAsciidocPlugin() {
        apply<AsciidoctorJPlugin>()
    }

    private
    fun Project.configureAsciidoctorTask() {
        tasks.named<AsciidoctorTask>("asciidoctor") {
            baseDirFollowsSourceDir()

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
    }
}