package com.bmuschko.gradle.docker.internal

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput

@CompileStatic
final class ConventionPluginHelper {

    private ConventionPluginHelper() {}

    static SourceSetOutput getMainJavaSourceSetOutput(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention)
        javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).output
    }

    static CopySpec createAppFilesCopySpec(Project project) {
        project.copySpec(new Action<CopySpec>() {
            @Override
            void execute(CopySpec rootSpec) {
                rootSpec.into('libs', new Action<CopySpec>() {
                    @Override
                    void execute(CopySpec copySpec) {
                        copySpec.from(getRuntimeClasspathConfiguration(project))
                    }
                })
                rootSpec.into('resources', new Action<CopySpec>() {
                    @Override
                    void execute(CopySpec copySpec) {
                        copySpec.from(getMainJavaSourceSetOutput(project).resourcesDir)
                    }
                })
                rootSpec.into('classes', new Action<CopySpec>() {
                    @Override
                    void execute(CopySpec copySpec) {
                        copySpec.from(getMainJavaSourceSetOutput(project).classesDirs)
                    }
                })
            }
        })
    }

    private static Configuration getRuntimeClasspathConfiguration(Project project) {
        project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
    }
}
