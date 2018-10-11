package com.bmuschko.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.plugins.quality.CodeNarcExtension
import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.kotlin.dsl.*

class StaticCodeAnalysisPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyCodenarcPlugin()
        configureCodeNarcExtension()
        configureCodeNarcTasks()
        configureCodeMainTask()
        disableCodeTestTasks()
    }

    private
    fun Project.applyCodenarcPlugin() {
        apply<CodeNarcPlugin>()
    }

    private
    fun Project.configureCodeNarcExtension() {
        configure<CodeNarcExtension> {
            reportFormat = "console"
            toolVersion = "1.1"
            configFile = rootProject.file("config/codenarc/rules.groovy")
        }
    }

    private
    fun Project.configureCodeNarcTasks() {
        tasks.withType<CodeNarc>().configureEach {
            group = "verification"
        }
    }

    private
    fun Project.configureCodeMainTask() {
        tasks.named<CodeNarc>("codenarcMain") {
            ignoreFailures = true
            include("**/utils/*.groovy")
        }
    }

    private
    fun Project.disableCodeTestTasks() {
        val codenarcTestSetup: CodeNarc by tasks.getting
        val codenarcTest: CodeNarc by tasks.getting
        val codenarcIntegrationTest: CodeNarc by tasks.getting
        val codenarcFunctionalTest: CodeNarc by tasks.getting
        val codenarcDocTest: CodeNarc by tasks.getting

        configure(setOf(codenarcTestSetup, codenarcTest, codenarcIntegrationTest, codenarcFunctionalTest, codenarcDocTest)) {
            enabled = false
        }
    }
}