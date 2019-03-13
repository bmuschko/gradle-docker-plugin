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
import org.gradle.api.file.CopySpec
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync

import java.util.concurrent.Callable

import static com.bmuschko.gradle.docker.utils.ConventionPluginHelper.*

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Java application.
 */
@CompileStatic
class DockerJavaApplicationPlugin implements Plugin<Project> {
    public static final String JAVA_APPLICATION_EXTENSION_NAME = 'javaApplication'
    public static final String SYNC_BUILD_CONTEXT_TASK_NAME = 'dockerSyncBuildContext'
    public static final String DOCKERFILE_TASK_NAME = 'dockerCreateDockerfile'
    public static final String BUILD_IMAGE_TASK_NAME = 'dockerBuildImage'
    public static final String PUSH_IMAGE_TASK_NAME = 'dockerPushImage'

    @Override
    void apply(Project project) {
        project.plugins.apply(DockerRemoteApiPlugin)
        DockerExtension dockerExtension = project.extensions.getByType(DockerExtension)
        DockerJavaApplication dockerJavaApplication = configureExtension(project, dockerExtension)

        project.plugins.withType(ApplicationPlugin) {
            dockerJavaApplication.exec(new Action<DockerJavaApplication.CompositeExecInstruction>() {
                @Override
                void execute(DockerJavaApplication.CompositeExecInstruction compositeExecInstruction) {
                    compositeExecInstruction.entryPoint(project.provider(new Callable<List<String>>() {
                        @Override
                        List<String> call() throws Exception {
                            ["java", "-cp", "/app/resources:/app/classes:/app/libs/*", getApplicationPluginMainClassName(project)]
                        }
                    }))
                }
            })
            Dockerfile createDockerfileTask = createDockerfileTask(project, dockerJavaApplication)
            Sync syncBuildContextTask = createSyncBuildContextTask(project, createDockerfileTask)
            createDockerfileTask.dependsOn syncBuildContextTask
            DockerBuildImage dockerBuildImageTask = createBuildImageTask(project, createDockerfileTask, dockerJavaApplication)
            createPushImageTask(project, dockerBuildImageTask)
        }
    }

    private static DockerJavaApplication configureExtension(Project project, DockerExtension dockerExtension) {
        ((ExtensionAware) dockerExtension).extensions.create(JAVA_APPLICATION_EXTENSION_NAME, DockerJavaApplication, project)
    }

    private static Dockerfile createDockerfileTask(Project project, DockerJavaApplication dockerJavaApplication) {
        project.tasks.create(DOCKERFILE_TASK_NAME, Dockerfile, new Action<Dockerfile>() {
            @Override
            void execute(Dockerfile dockerfile) {
                dockerfile.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Creates the Docker image for the Java application.'
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
                    workingDir('/app')
                    copyFile(project.provider(new Callable<Dockerfile.File>() {
                        @Override
                        Dockerfile.File call() throws Exception {
                            if (new File(dockerfile.destDir.get().asFile, 'libs').isDirectory()) {
                                return new Dockerfile.File('libs', 'libs/')
                            }
                        }
                    }))
                    copyFile(project.provider(new Callable<Dockerfile.File>() {
                        @Override
                        Dockerfile.File call() throws Exception {
                            if (getMainJavaSourceSetOutput(project).resourcesDir.isDirectory()) {
                                return new Dockerfile.File('resources', 'resources/')
                            }
                        }
                    }))
                    copyFile('classes', 'classes/')
                    instructions.add(dockerJavaApplication.execInstruction)
                    exposePort(dockerJavaApplication.ports)
                }
            }
        })
    }

    private static Sync createSyncBuildContextTask(Project project, Dockerfile createDockerfileTask) {
        project.tasks.create(SYNC_BUILD_CONTEXT_TASK_NAME, Sync, new Action<Sync>() {
            @Override
            void execute(Sync sync) {
                sync.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = "Copies the distribution resources to a temporary directory for image creation."
                    dependsOn project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME)
                    into(createDockerfileTask.destDir)
                    into('libs', new Action<CopySpec>() {
                        @Override
                        void execute(CopySpec copySpec) {
                            copySpec.from(getRuntimeClasspathConfiguration(project))
                        }
                    })
                    into('resources', new Action<CopySpec>() {
                        @Override
                        void execute(CopySpec copySpec) {
                            copySpec.from(getMainJavaSourceSetOutput(project).resourcesDir)
                        }
                    })
                    into('classes', new Action<CopySpec>() {
                        @Override
                        void execute(CopySpec copySpec) {
                            copySpec.from(getMainJavaSourceSetOutput(project).classesDirs)
                        }
                    })
                }
            }
        })
    }

    private static DockerBuildImage createBuildImageTask(Project project, Dockerfile createDockerfileTask, DockerJavaApplication dockerJavaApplication) {
        project.tasks.create(BUILD_IMAGE_TASK_NAME, DockerBuildImage, new Action<DockerBuildImage>() {
            @Override
            void execute(DockerBuildImage dockerBuildImage) {
                dockerBuildImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Builds the Docker image for the Java application.'
                    dependsOn createDockerfileTask
                    tags.add(determineImageTag(project, dockerJavaApplication))
                }
            }
        })
    }

    private static Provider<String> determineImageTag(Project project, DockerJavaApplication dockerJavaApplication) {
        project.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                if (dockerJavaApplication.tag.getOrNull()) {
                    return dockerJavaApplication.tag.get()
                }

                String tagVersion = project.version == 'unspecified' ? 'latest' : project.version
                final String applicationName = getApplicationPluginName(project)
                String artifactAndVersion = "${applicationName}:${tagVersion}".toLowerCase().toString()
                project.group ? "$project.group/$artifactAndVersion".toString() : artifactAndVersion
            }
        })
    }

    private static void createPushImageTask(Project project, DockerBuildImage dockerBuildImageTask) {
        project.tasks.create(PUSH_IMAGE_TASK_NAME, DockerPushImage, new Action<DockerPushImage>() {
            @Override
            void execute(DockerPushImage pushImage) {
                pushImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Pushes created Docker image to the repository.'
                    dependsOn dockerBuildImageTask
                    imageName.set(project.provider(new Callable<String>() {
                        @Override
                        String call() throws Exception {
                            dockerBuildImageTask.getTags().get().first() as String
                        }
                    }))
                }
            }
        })
    }
}
