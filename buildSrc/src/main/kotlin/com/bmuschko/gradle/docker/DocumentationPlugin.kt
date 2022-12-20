package com.bmuschko.gradle.docker

import org.ajoberstar.gradle.git.publish.GitPublishExtension
import org.ajoberstar.gradle.git.publish.GitPublishPlugin
import org.asciidoctor.gradle.jvm.AsciidoctorTask
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
        val javaApiUrl = "https://docs.oracle.com/en/java/javase/11/docs/api"
        val gradleApiUrl = "https://docs.gradle.org/${project.gradle.gradleVersion}/javadoc/"

        tasks.withType<Javadoc>().configureEach {
            (options as StandardJavadocDocletOptions).links(javaApiUrl, gradleApiUrl)
        }
    }

    private
    fun Project.configureGitPublishExtension() {
        val javadoc: Javadoc by tasks
        val asciidoctorUserGuide = tasks.named<AsciidoctorTask>("asciidoctorUserGuide").get()
        val asciidoctorDevGuide = tasks.named<AsciidoctorTask>("asciidoctorDevGuide").get()

        configure<GitPublishExtension> {
            repoUri = "https://github.com/bmuschko/gradle-docker-plugin.git"
            branch = "gh-pages"

            contents {
                preserve {
                    include("**/*")
                }
                from(javadoc) {
                    into("current/api")
                }
                from(javadoc) {
                    into(KotlinClosure0({ "${project.version}/api" }))
                }
                from(asciidoctorUserGuide.outputDir) {
                    into("current/user-guide")
                }
                from(asciidoctorUserGuide.outputDir) {
                    into(KotlinClosure0({ "${project.version}/user-guide" }))
                }
                from(asciidoctorDevGuide.outputDir) {
                    into("current/dev-guide")
                }
                from(asciidoctorDevGuide.outputDir) {
                    into(KotlinClosure0({ "${project.version}/dev-guide" }))
                }
            }
        }
    }

    private
    fun Project.configureTaskDependencies() {
        val javadoc: Javadoc by tasks
        val asciidoctor: AsciidoctorTask by tasks
        tasks["gitPublishCopy"].dependsOn(javadoc, asciidoctor)
    }
}
