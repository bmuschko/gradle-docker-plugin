package com.bmuschko.gradle.docker.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput

final class ConventionPluginHelper {

    private ConventionPluginHelper() {}

    static String getApplicationPluginName(Project project) {
        project.convention.getPlugin(ApplicationPluginConvention).applicationName
    }

    static String getApplicationPluginMainClassName(Project project) {
        project.convention.getPlugin(ApplicationPluginConvention).mainClassName
    }

    static SourceSetOutput getMainJavaSourceSetOutput(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention)
        javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).output
    }

    static Configuration getRuntimeClasspathConfiguration(Project project) {
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    }
}
