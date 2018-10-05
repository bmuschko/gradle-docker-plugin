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

        configure<CodeNarcExtension> {
            reportFormat = "console"
            toolVersion = "1.1"
            configFile = rootProject.file("$project.rootDir/config/codenarc/rules.groovy")
        }

        tasks.named<CodeNarc>("codenarcMain") {
            group = "verification"
            ignoreFailures = true
            include("**/utils/*.groovy")
        }

        val codenarcTestSetup: CodeNarc by tasks.getting
        val codenarcTest: CodeNarc by tasks.getting
        val codenarcIntegrationTest: CodeNarc by tasks.getting
        val codenarcFunctionalTest: CodeNarc by tasks.getting

        configure(setOf(codenarcTestSetup, codenarcTest, codenarcIntegrationTest, codenarcFunctionalTest)) {
            group = "verification"
            enabled = false
        }
    }

    private
    fun Project.applyCodenarcPlugin() {
        plugins.apply(CodeNarcPlugin::class.java)
    }
}