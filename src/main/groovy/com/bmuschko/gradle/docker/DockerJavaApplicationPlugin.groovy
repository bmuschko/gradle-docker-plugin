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
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.file.CopySpec
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPluginConvention
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar

import java.util.concurrent.Callable

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Java application.
 */
@CompileStatic
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
        DockerJavaApplication dockerJavaApplication = configureExtension(project, dockerExtension)

        project.plugins.withType(ApplicationPlugin) {
            Sync installTask = project.tasks.getByName(DistributionPlugin.TASK_INSTALL_NAME) as Sync
            Jar jarTask = project.tasks.getByName(JavaPlugin.JAR_TASK_NAME) as Jar
            dockerJavaApplication.exec(new Action<DockerJavaApplication.CompositeExecInstruction>() {
                @Override
                void execute(DockerJavaApplication.CompositeExecInstruction compositeExecInstruction) {
                    compositeExecInstruction.entryPoint(determineEntryPoint(project, installTask))
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
     * @param project Project
     * @param dockerExtension Docker extension
     * @return Java application configuration
     */
    private static DockerJavaApplication configureExtension(Project project, DockerExtension dockerExtension) {
        ((ExtensionAware) dockerExtension).extensions.create(JAVA_APPLICATION_EXTENSION_NAME, DockerJavaApplication, project)
    }

    private static Action<Dockerfile> createDockerfileTaskAction(
        Project project,
        Sync installTask,
        Jar jarTask,
        DockerJavaApplication dockerJavaApplication) {
        return new Action<Dockerfile>() {
            @Override
            void execute(Dockerfile dockerfile) {
                dockerfile.with {
                    description = 'Creates the Docker image for the Java application.'
                    dependsOn jarTask
                    from(project.provider(new Callable<Dockerfile.From>() {
                        @Override
                        Dockerfile.From call() throws Exception {
                            new Dockerfile.From(dockerJavaApplication.baseImage.get())
                        }
                    }))
                    label(project.provider(new Callable<Map<String, String>>() {
                        @Override
                        Map<String, String> call() throws Exception {
                            ['maintainer': dockerJavaApplication.maintainer.get()]
                        }
                    }))
                    addFile(project.provider(new Callable<Dockerfile.File>() {
                        @Override
                        Dockerfile.File call() throws Exception {
                            new Dockerfile.File(installTask.destinationDir.name, "/${installTask.destinationDir.name}".toString())
                        }
                    }))
                    addFile(project.provider(new Callable<Dockerfile.File>() {
                        @Override
                        Dockerfile.File call() throws Exception {
                            new Dockerfile.File("app-lib/${jarTask.archiveName}".toString(), "/${installTask.destinationDir.name}/lib/${jarTask.archiveName}".toString())
                        }
                    }))
                    instructions.add(dockerJavaApplication.execInstruction)
                    exposePort(dockerJavaApplication.ports)
                }

            }
        }
    }

    private static Dockerfile createDockerfileTask(
        Project project,
        Sync installTask,
        Jar jarTask,
        DockerJavaApplication dockerJavaApplication) {
        project.tasks.create(DOCKERFILE_TASK_NAME, Dockerfile, createDockerfileTaskAction(project, installTask, jarTask, dockerJavaApplication))
    }

    private static Action<Sync> createDistCopyResourcesTaskAction(
        Sync installTask,
        Jar jarTask,
        Dockerfile createDockerfileTask) {
        return new Action<Sync>() {
            @Override
            void execute(Sync sync) {
                sync.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = "Copies the distribution resources to a temporary directory for image creation."
                    dependsOn installTask
                    from { installTask.destinationDir.parentFile }
                    into { createDockerfileTask.destFile.get().asFile.parentFile }
                    exclude "**/lib/${jarTask.archiveName}"
                    into("app-lib", new Action<CopySpec>() {
                        @Override
                        void execute(CopySpec copySpec) {
                            copySpec.from jarTask
                        }
                    })
                }
            }
        }
    }

    private Sync createDistCopyResourcesTask(Project project, Sync installTask, Jar jarTask, Dockerfile createDockerfileTask) {
        project.tasks.create(
            COPY_DIST_RESOURCES_TASK_NAME,
            Sync,
            createDistCopyResourcesTaskAction(installTask, jarTask, createDockerfileTask))
    }

    private Provider<List<String>> determineEntryPoint(Project project, Sync installTask) {
        project.provider(new Callable<List<String>>() {
            @Override
            List<String> call() throws Exception {
                final String applicationName = getApplicationName(project)
                ["/${installTask.destinationDir.name}/bin/${applicationName}".toString()]
            }
        })
    }

    private DockerBuildImage createBuildImageTask(Project project, Dockerfile createDockerfileTask, DockerJavaApplication dockerJavaApplication) {
        project.tasks.create(BUILD_IMAGE_TASK_NAME, DockerBuildImage, new Action<DockerBuildImage>() {
            @Override
            void execute(DockerBuildImage dockerBuildImage) {
                dockerBuildImage.with {
                    description = 'Builds the Docker image for the Java application.'
                    dependsOn createDockerfileTask
                    inputDir.set(project.provider(new Callable<Directory>() {
                        @Override
                        Directory call() throws Exception {
                            project.layout.projectDirectory.dir(createDockerfileTask.destFile.get().asFile.parentFile.canonicalPath)
                        }
                    }))
                }
                // Can't be within `with` above because the compiler falls over.
                dockerBuildImage.tags.set(determineImageTags(project, dockerJavaApplication))
            }
        })
    }

    private static Provider<Set<String>> determineImageTags(Project project, DockerJavaApplication dockerJavaApplication) {
        project.provider(new Callable<Set<String>>() {
            @Override
            Set<String> call() throws Exception {
                if (dockerJavaApplication.tag.getOrNull()) {
                    return [dockerJavaApplication.tag.get()].toSet();
                }
                if (dockerJavaApplication.tags.getOrNull()) {
                    return dockerJavaApplication.tags.get()
                }

                String tagVersion = project.version == 'unspecified' ? 'latest' : project.version
                final String applicationName = getApplicationName(project)
                String artifactAndVersion = "${applicationName}:${tagVersion}".toLowerCase().toString()
                [project.group ? "$project.group/$artifactAndVersion".toString() : artifactAndVersion].toSet()
            }
        })
    }

    private static void createPushImageTask(Project project, DockerBuildImage dockerBuildImageTask) {
        project.tasks.create(PUSH_IMAGE_TASK_NAME, DockerPushImage, new Action<DockerPushImage>() {
            @Override
            void execute(DockerPushImage pushImage) {
                pushImage.with {
                    description = 'Pushes created Docker image to the repository.'
                    dependsOn dockerBuildImageTask
                    imageName.set(dockerBuildImageTask.getTag())
                }
            }
        })
    }

    private static String getApplicationName(Project project) {
        project.convention.getPlugin(ApplicationPluginConvention).applicationName
    }
}
