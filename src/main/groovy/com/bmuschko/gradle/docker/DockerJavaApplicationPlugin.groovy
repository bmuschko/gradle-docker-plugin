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
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Java application.
 */
class DockerJavaApplicationPlugin implements Plugin<Project> {
    public static final String JAVA_APPLICATION_EXTENSION_NAME = "javaApplication"
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
            Sync installTask = project.tasks.getByName(DistributionPlugin.TASK_INSTALL_NAME)
            Jar jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME)
            dockerJavaApplication.exec(new Action<DockerJavaApplication.CompositeExecInstruction>() {
                @Override
                void execute(DockerJavaApplication.CompositeExecInstruction compositeExecInstruction) {
                    compositeExecInstruction.entryPoint { determineEntryPoint(project, installTask) }
                }
            })
            Dockerfile createDockerfileTask = createDockerfileTask(project, installTask, jarTask, dockerJavaApplication)
            Sync copyTarTask = createDistCopyResourcesTask(project, installTask, jarTask, createDockerfileTask)
            createDockerfileTask.dependsOn copyTarTask
            DockerBuildImage dockerBuildImageTask = createBuildImageTask(project, createDockerfileTask, dockerJavaApplication)
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
        ((ExtensionAware) dockerExtension).extensions.create(JAVA_APPLICATION_EXTENSION_NAME, DockerJavaApplication)
    }

    private Dockerfile createDockerfileTask(Project project, Sync installTask, Jar jarTask, DockerJavaApplication dockerJavaApplication) {
        project.task(DOCKERFILE_TASK_NAME, type: Dockerfile) {
            description = 'Creates the Docker image for the Java application.'
            dependsOn jarTask
            from { dockerJavaApplication.baseImage }
            label { ['maintainer': dockerJavaApplication.maintainer] }
            addFile({ installTask.destinationDir.name }, { "/${installTask.destinationDir.name}" })
            addFile({ "app-lib/${jarTask.archiveName}" }, { "/${installTask.destinationDir.name}/lib/${jarTask.archiveName}" })
            instructions << dockerJavaApplication.execInstruction
            doFirst {
                if (dockerJavaApplication.getPorts().length > 0) {
                    exposePort { dockerJavaApplication.getPorts() }
                }
                if(dockerJavaApplication.skipMaintainer) {
                    instructions.removeAll { it instanceof Dockerfile.MaintainerInstruction }
                }
            }
        } as Dockerfile
    }

    private Sync createDistCopyResourcesTask(Project project, Sync installTask, Jar jarTask, Dockerfile createDockerfileTask) {
        project.task(COPY_DIST_RESOURCES_TASK_NAME, type: Sync) {
            group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
            description = "Copies the distribution resources to a temporary directory for image creation."
            dependsOn installTask
            from { installTask.destinationDir.parentFile }
            into { createDockerfileTask.destFile.parentFile }
            exclude "**/lib/${jarTask.archiveName}"
            into("app-lib") {
                from jarTask
            }
        } as Sync
    }

    private String determineEntryPoint(Project project, Sync installTask) {
        "/${installTask.destinationDir.name}/bin/${project.applicationName}".toString()
    }

    private DockerBuildImage createBuildImageTask(Project project, Dockerfile createDockerfileTask, DockerJavaApplication dockerJavaApplication) {
        project.task(BUILD_IMAGE_TASK_NAME, type: DockerBuildImage) {
            description = 'Builds the Docker image for the Java application.'
            dependsOn createDockerfileTask
            conventionMapping.inputDir = { createDockerfileTask.destFile.parentFile }
            conventionMapping.tag = { determineImageTag(project, dockerJavaApplication) }
        } as DockerBuildImage
    }

    private String determineImageTag(Project project, DockerJavaApplication dockerJavaApplication) {
        if (dockerJavaApplication.tag) {
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
