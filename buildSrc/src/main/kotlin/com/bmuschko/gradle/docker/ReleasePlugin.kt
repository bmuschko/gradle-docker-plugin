package com.bmuschko.gradle.docker

import org.ajoberstar.gradle.git.base.GrgitPlugin
import org.ajoberstar.gradle.git.release.opinion.OpinionReleasePlugin
import org.ajoberstar.gradle.git.release.base.ReleasePluginExtension
import org.ajoberstar.gradle.git.release.base.ReleaseVersion
import org.ajoberstar.gradle.git.release.base.TagStrategy
import org.ajoberstar.gradle.git.release.opinion.Strategies
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.delegateClosureOf

class ReleasePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyGrgitPlugin()
        configureReleasePluginExtension()
    }

    private
    fun Project.applyGrgitPlugin() {
        apply<OpinionReleasePlugin>()
        apply<GrgitPlugin>()
    }

    private
    fun Project.configureReleasePluginExtension() {
        configure<ReleasePluginExtension> {
            versionStrategy(Strategies.getFINAL())
            defaultVersionStrategy = Strategies.getSNAPSHOT()
            tagStrategy(delegateClosureOf<TagStrategy> {
                generateMessage = closureOf<ReleaseVersion> {
                    "Version ${project.version}"
                }
            })
        }
    }
}
