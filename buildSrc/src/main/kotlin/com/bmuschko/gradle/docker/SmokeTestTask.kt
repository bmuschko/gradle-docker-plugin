package com.bmuschko.gradle.docker

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provideDelegate
import org.intellij.lang.annotations.Language
import java.io.File

abstract class SmokeTestTask : DefaultTask() {

    private val versionsToTest = listOf(
        //"5.0", "5.1", "5.1.1", "5.2", "5.2.1", "5.3", "5.3.1",
        //"5.4", "5.4.1", "5.5", "5.5.1",
        //"5.6", "5.6.1", "5.6.2", "5.6.3", "5.6.4",
        //"6.0", "6.0.1", "6.1", "6.1.1", "6.2", "6.2.1", "6.2.2",
        //"6.3", "6.4", "6.4.1", "6.5", "6.5.1",
        //"6.6", "6.6.1", "6.7", "6.7.1", "6.8", "6.8.1", "6.8.2", "6.8.3",
        "6.9", "6.9.1", "6.9.2",
        "7.0", "7.0.1", "7.0.2", "7.1", "7.1.1", "7.2",
        "7.3", "7.3.1", "7.3.2", "7.3.3", "7.4", "7.4.1", "7.4.2",
        "7.5", "7.5.1",
    )

    @get:Input
    @get:Optional
    abstract val smokeTestIncludes: Property<String>

    @TaskAction
    fun action() {
        // handle regex to only include certain gradle versions
        val versionsToTest = when (val pattern = smokeTestIncludes.orNull) {
            is String -> {
                val regex = Regex(pattern)
                versionsToTest.filter { regex.find(it) != null }
            }
            else -> versionsToTest
        }

        for (vtt in versionsToTest) {
            testGradle(vtt)
        }
    }

    fun testGradle(version: String) {
        val testProjectDir = File(temporaryDir, version)

        if (testProjectDir.exists()) {
            project.delete(testProjectDir)
        }

        val testProjectMainSrc = File(testProjectDir, "src/main/java")
        testProjectMainSrc.mkdirs()
        val testProjectMainFile = File(testProjectMainSrc, "Main.java")

        @Language(value = "java") val testProjectMain = """
                public class Main {
                    public static void main(String[] args){
                        System.out.println("Hello, world!");
                    }
                }
                """.trimIndent()
        testProjectMainFile.writeText(testProjectMain)

        testProjectDir.mkdirs()
        val testProjectBuildScript = File(testProjectDir, "build.gradle")
        println("${project.group}:${project.name}:${project.version}")
        @Language(value = "groovy") val testProjectBuild = """
                    buildscript {
                        dependencies {
                            classpath '${project.group}:${project.name}:${project.version}'
                        }
                        repositories {
                            mavenLocal()
                            mavenCentral()
                        }
                    }
                    apply plugin: 'java'
                    apply plugin: 'application'
                    apply plugin: 'com.bmuschko.docker-java-application'
                """.trimIndent()
        testProjectBuildScript.writeText(testProjectBuild)

        // write empty settings.gradle for Gradle 5.x and higher
        File(testProjectDir, "settings.gradle").writeText("")

        // execute Gradle to create a Gradle wrapper for the test project
        var gradlew = listOf(File(project.rootDir, "gradlew").getAbsolutePath())
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            gradlew = listOf("cmd", "/c", File(project.rootDir, "gradlew.bat").getAbsolutePath())
        }
        project.exec {
            workingDir = testProjectDir
            commandLine = gradlew + listOf("--no-daemon", "--stacktrace", "wrapper", "--gradle-version", version)
        }

        // execute test project
        gradlew = listOf(File(testProjectDir, "gradlew").getAbsolutePath())
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            gradlew = listOf("cmd", "/c", File(testProjectDir, "gradlew.bat").getAbsolutePath())
        }
        project.exec {
            workingDir = testProjectDir
            commandLine = gradlew + listOf("--version")
        }
        val execArgs = listOf("--no-daemon", "--stacktrace", "dockerBuildImage")
        project.exec {
            workingDir = testProjectDir
            commandLine = gradlew + execArgs
        }

        // execute built docker image
        val imageId = testProjectDir.resolve("build/.docker/dockerBuildImage-imageId.txt").readText()
        project.exec {
            commandLine = listOf("docker", "run", "-i", "--rm", imageId)
        }

        project.delete(testProjectDir)
    }
}
