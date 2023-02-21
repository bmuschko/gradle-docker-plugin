package com.bmuschko.gradle.docker.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;

public final class ConventionPluginHelper {

    private ConventionPluginHelper() {
    }

    public static SourceSetOutput getMainJavaSourceSetOutput(Project project) {
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        return javaPluginExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
    }

    public static CopySpec createAppFilesCopySpec(final Project project) {
        return project.copySpec(rootSpec -> {
            rootSpec.into("libs", copySpec -> copySpec.from(getRuntimeClasspathConfiguration(project)));
            rootSpec.into("resources", copySpec -> copySpec.from(getMainJavaSourceSetOutput(project).getResourcesDir()));
            rootSpec.into("classes", copySpec -> copySpec.from(getMainJavaSourceSetOutput(project).getClassesDirs()));
        });
    }

    private static Configuration getRuntimeClasspathConfiguration(Project project) {
        return project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
    }
}
