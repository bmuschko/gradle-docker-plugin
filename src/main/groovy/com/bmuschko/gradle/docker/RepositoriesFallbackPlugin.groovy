package com.bmuschko.gradle.docker

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

@CompileStatic
class RepositoriesFallbackPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // if no repositories were defined fallback to buildscript
        // repositories to resolve dependencies as a last resort
        project.afterEvaluate {
            if (project.repositories.size() == 0) {
                project.repositories.addAll(project.buildscript.repositories.collect())
            }

            // if still 0 attempt to grab rootProject buildscript repos
            if (project.repositories.size() == 0) {
                project.repositories.addAll(project.rootProject.buildscript.repositories.collect())
            }

            // and if still 0 attempt to grab rootProject repos
            if (project.repositories.size() == 0) {
                project.repositories.addAll(project.rootProject.repositories.collect())
            }
        }
    }
}
