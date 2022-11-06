package com.bmuschko.gradle.docker.internal;

import groovy.transform.CompileStatic;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;

@CompileStatic
public final class ConventionPluginHelper {

    private ConventionPluginHelper() {
    }

    public static SourceSetOutput getMainJavaSourceSetOutput(Project project) {
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        return javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
    }

    public static CopySpec createAppFilesCopySpec(final Project project) {
        return project.copySpec(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec rootSpec) {
                rootSpec.into("libs", new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.from(getRuntimeClasspathConfiguration(project));
                    }

                });
                rootSpec.into("resources", new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.from(getMainJavaSourceSetOutput(project).getResourcesDir());
                    }

                });
                rootSpec.into("classes", new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec copySpec) {
                        copySpec.from(getMainJavaSourceSetOutput(project).getClassesDirs());
                    }

                });
            }

        });
    }

    private static Configuration getRuntimeClasspathConfiguration(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
    }
}
