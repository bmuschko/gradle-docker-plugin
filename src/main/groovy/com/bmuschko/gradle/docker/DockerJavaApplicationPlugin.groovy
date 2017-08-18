/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Tar
/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Java application.
 */
class DockerJavaApplicationPlugin implements Plugin<Project> {
    static final String JAVA_APPLICATION_EXTENSION_NAME = "javaApplication"
    public static final String COPY_DIST_RESOURCES_TASK_NAME = 'dockerCopyDistResources'
    public static final String DOCKERFILE_TASK_NAME = 'dockerDistTar'
    public static final String BUILD_IMAGE_TASK_NAME = 'dockerBuildImage'
    public static final String PUSH_IMAGE_TASK_NAME = 'dockerPushImage'

    @Override
    void apply(Project project) {
        project.apply(plugin: DockerRemoteApiPlugin)

        DockerExtension dockerExtension = project.extensions.getByType(DockerExtension)
        DockerJavaApplication dockerJavaApplication = configureExtension(dockerExtension)

        project.plugins.withType(ApplicationPlugin) {
            Tar tarTask = project.tasks.getByName(ApplicationPlugin.TASK_DIST_TAR_NAME)
            dockerJavaApplication.exec {
                entryPoint { determineEntryPoint(project, tarTask) }
            }
            Dockerfile createDockerfileTask = createDockerfileTask(project, tarTask, dockerJavaApplication)
            Copy copyTarTask = createDistCopyResourcesTask(project, tarTask, createDockerfileTask)
            createDockerfileTask.dependsOn copyTarTask
            DockerBuildImage dockerBuildImageTask = createBuildImageTask(project, tarTask, copyTarTask, createDockerfileTask, dockerJavaApplication)
            createPushImageTask(project, dockerBuildImageTask)
        }
    }

    /**
     * Configure existing Docker extension by adding properties for Java-based application.
     *
     * @param dockerExtension Docker extension
     * @return Java application configuration
     */
    private static DockerJavaApplication configureExtension(DockerExtension dockerExtension) {
        dockerExtension.extensions.create(JAVA_APPLICATION_EXTENSION_NAME, DockerJavaApplication)
        return dockerExtension.javaApplication
    }

    private Dockerfile createDockerfileTask(Project project, Tar tarTask, DockerJavaApplication dockerJavaApplication) {
        project.task(DOCKERFILE_TASK_NAME, type: Dockerfile) {
            description = 'Creates the Docker image for the Java application.'
            dependsOn tarTask
            from { dockerJavaApplication.baseImage }
            maintainer { dockerJavaApplication.maintainer }
            addFile({ tarTask.archivePath.name }, { '/' })
            instructions << dockerJavaApplication.exec
            exposePort { dockerJavaApplication.getPorts() }
        }
    }

    private Copy createDistCopyResourcesTask(Project project, Tar tarTask, Dockerfile createDockerfileTask) {
        project.task(COPY_DIST_RESOURCES_TASK_NAME, type: Copy) {
            group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
            description = "Copies the distribution resources to a temporary directory for image creation."
            dependsOn tarTask
            from { tarTask.archivePath }
            into { createDockerfileTask.destFile.parentFile }
        }
    }

    private String determineEntryPoint(Project project, Tar tarTask) {
        String installDir = tarTask.archiveName - ".${tarTask.extension}"
        "/$installDir/bin/$project.applicationName".toString()
    }

    private DockerBuildImage createBuildImageTask(Project project, Tar tarTask, Copy copyTarTask, Dockerfile createDockerfileTask, DockerJavaApplication dockerJavaApplication) {
        project.task(BUILD_IMAGE_TASK_NAME, type: DockerBuildImage) {
            onlyIf {
                !copyTarTask.state.upToDate ||
                !createDockerfileTask.state.upToDate ||
                // any generic instruction we can't check
                createDockerfileTask.instructions.any { it instanceof Dockerfile.GenericInstruction } ||
                // any add instruction that not comes from the plugin
                createDockerfileTask.instructions.any { it instanceof Dockerfile.AddFileInstruction && !((it as Dockerfile.AddFileInstruction).src instanceof Closure && (it as Dockerfile.AddFileInstruction).src() == tarTask.archivePath.name) } ||
                // any copy instruction that not comes from the plugin
                createDockerfileTask.instructions.any { it instanceof Dockerfile.CopyFileInstruction }
            }
            description = 'Builds the Docker image for the Java application.'
            dependsOn createDockerfileTask
            conventionMapping.inputDir = { createDockerfileTask.destFile.parentFile }
            conventionMapping.tag = { determineImageTag(project, dockerJavaApplication) }
        }
    }

    private String determineImageTag(Project project, DockerJavaApplication dockerJavaApplication) {
        if(dockerJavaApplication.tag) {
            return dockerJavaApplication.tag
        }

        String tagVersion = project.version == 'unspecified' ? 'latest' : project.version
        String artifactAndVersion = "${project.applicationName}:${tagVersion}".toLowerCase().toString()
        project.group ? "$project.group/$artifactAndVersion".toString() : artifactAndVersion
    }

    private void createPushImageTask(Project project, DockerBuildImage dockerBuildImageTask) {
        project.task(PUSH_IMAGE_TASK_NAME, type: DockerPushImage) {
            description = 'Pushes created Docker image to the repository.'
            dependsOn dockerBuildImageTask
            conventionMapping.imageName = { dockerBuildImageTask.getTag() }
        }
    }
}
