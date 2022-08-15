package com.bmuschko.gradle.docker

import org.asciidoctor.gradle.jvm.AsciidoctorJPlugin
import org.asciidoctor.gradle.jvm.AsciidoctorTask
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named
import java.io.File

class UserGuidePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyAsciidocPlugin()
        disableAsciidoctorTask()
        val asciidoctorUserGuide = createAsciidoctorUserGuideTask()
        val asciidoctorDevGuide = createAsciidoctorDevGuideTask()
        createAllAsciidoctorUserGuideTask(asciidoctorUserGuide, asciidoctorDevGuide)
    }

    private
    fun Project.applyAsciidocPlugin() {
        apply<AsciidoctorJPlugin>()
    }

    private
    fun Project.disableAsciidoctorTask() {
        tasks.named<AsciidoctorTask>("asciidoctor").configure(Action { enabled = false })
    }

    private
    fun Project.createAsciidoctorUserGuideTask(): TaskProvider<AsciidoctorTask> {
        return createAsciidoctorTask("asciidoctorUserGuide", file("src/docs/asciidoc/user-guide"))
    }

    private
    fun Project.createAsciidoctorDevGuideTask(): TaskProvider<AsciidoctorTask> {
        return createAsciidoctorTask("asciidoctorDevGuide", file("src/docs/asciidoc/dev-guide"))
    }

    private
    fun Project.createAsciidoctorTask(taskName: String, sourceDir: File): TaskProvider<AsciidoctorTask> {
        return tasks.register(taskName, AsciidoctorTask::class.java) {
            setSourceDir(sourceDir)
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

    private
    fun Project.createAllAsciidoctorUserGuideTask(userGuideTask: TaskProvider<AsciidoctorTask>, devGuideTask: TaskProvider<AsciidoctorTask>) {
        tasks.register("asciidoctorAllGuides").configure(Action {
            dependsOn(userGuideTask)
            dependsOn(devGuideTask)
        })
    }
}