/*
 * Copyright 2019 the original author or authors.
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

import static com.bmuschko.gradle.docker.internal.ConventionPluginHelper.createAppFilesCopySpec
import static com.bmuschko.gradle.docker.internal.ConventionPluginHelper.getMainJavaSourceSetOutput

import java.util.concurrent.Callable

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
/**
 * The abstract class for all conventional JVM application plugins.
 *
 * @since 5.2.0
 */
@CompileStatic
abstract class DockerConventionJvmApplicationPlugin<EXT extends DockerConventionJvmApplicationExtension> implements Plugin<Project> {

    /**
     * The task name that copies the application files to a temporary directory for image creation.
     */
    public static final String SYNC_BUILD_CONTEXT_TASK_NAME = 'dockerSyncBuildContext'

    /**
     * The task name that creates the Docker image for the Java application.
     */
    public static final String DOCKERFILE_TASK_NAME = 'dockerCreateDockerfile'

    /**
     * The task name that builds the Docker image for the Java application.
     */
    public static final String BUILD_IMAGE_TASK_NAME = 'dockerBuildImage'

    /**
     * The task name that pushes created Docker image to the repository.
     */
    public static final String PUSH_IMAGE_TASK_NAME = 'dockerPushImage'

    @Override
    void apply(Project project) {
        project.plugins.apply(DockerRemoteApiPlugin)
        DockerExtension dockerExtension = project.extensions.getByType(DockerExtension)
        EXT extension = configureExtension(project.objects, dockerExtension)

        project.plugins.withType(JavaPlugin) {
            TaskProvider<Dockerfile> createDockerfileTask = registerDockerfileTask(project, extension)
            TaskProvider<Sync> syncBuildContextTask = registerSyncBuildContextTask(project, createDockerfileTask)
            createDockerfileTask.configure(new Action<Dockerfile>() {
                @Override
                void execute(Dockerfile dockerfile) {
                    dockerfile.dependsOn(syncBuildContextTask)
                }
            })
            TaskProvider<DockerBuildImage> dockerBuildImageTask = registerBuildImageTask(project, createDockerfileTask, extension)
            registerPushImageTask(project, dockerBuildImageTask)
        }
    }

    private TaskProvider<Dockerfile> registerDockerfileTask(Project project, EXT extension) {
        project.tasks.register(DOCKERFILE_TASK_NAME, Dockerfile, new Action<Dockerfile>() {
            @Override
            void execute(Dockerfile dockerfile) {
                dockerfile.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Creates the Docker image for the application.'
                    from(project.provider(new Callable<Dockerfile.From>() {
                        @Override
                        Dockerfile.From call() throws Exception {
                            new Dockerfile.From(extension.baseImage.get())
                        }
                    }))
                    label(project.provider(new Callable<Map<String, String>>() {
                        @Override
                        Map<String, String> call() throws Exception {
                            ['maintainer': extension.maintainer.get()]
                        }
                    }))
                    workingDir('/app')
                    copyFile(project.provider(new Callable<Dockerfile.CopyFile>() {
                        @Override
                        Dockerfile.CopyFile call() throws Exception {
                            if (new File(dockerfile.destDir.get().asFile, 'libs').isDirectory()) {
                                return new Dockerfile.CopyFile('libs', 'libs/')
                            }
                        }
                    }))
                    copyFile(project.provider(new Callable<Dockerfile.CopyFile>() {
                        @Override
                        Dockerfile.CopyFile call() throws Exception {
                            if (getMainJavaSourceSetOutput(project).resourcesDir.isDirectory()) {
                                return new Dockerfile.CopyFile('resources', 'resources/')
                            }
                        }
                    }))
                    copyFile(new Dockerfile.CopyFile('classes', 'classes/'))
                    entryPoint(project.provider(new Callable<List<String>>() {
                        @Override
                        List<String> call() throws Exception {
                            List<String> entrypoint = ["java"]
                            List<String> jvmArgs = extension.jvmArgs.get()

                            if (!jvmArgs.empty) {
                                entrypoint.addAll(jvmArgs)
                            }

                            entrypoint.addAll(["-cp", "/app/resources:/app/classes:/app/libs/*", getApplicationMainClassName(project, extension)])
                            entrypoint
                        }
                    }))
                    exposePort(extension.ports)
                }
            }
        })
    }

    private static TaskProvider<Sync> registerSyncBuildContextTask(Project project, TaskProvider<Dockerfile> createDockerfileTask) {
        project.tasks.register(SYNC_BUILD_CONTEXT_TASK_NAME, Sync, new Action<Sync>() {
            @Override
            void execute(Sync sync) {
                sync.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = "Copies the distribution resources to a temporary directory for image creation."
                    dependsOn project.tasks.getByName(JavaPlugin.CLASSES_TASK_NAME)
                    into(project.provider(new Callable<Directory>() {
                        @Override
                        Directory call() throws Exception {
                            createDockerfileTask.get().destDir.get()
                        }
                    }))
                    with(createAppFilesCopySpec(project))
                }
            }
        })
    }

    private TaskProvider<DockerBuildImage> registerBuildImageTask(Project project, TaskProvider<Dockerfile> createDockerfileTask, EXT extension) {
        project.tasks.register(BUILD_IMAGE_TASK_NAME, DockerBuildImage, new Action<DockerBuildImage>() {
            @Override
            void execute(DockerBuildImage dockerBuildImage) {
                dockerBuildImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Builds the Docker image for the application.'
                    dependsOn createDockerfileTask
                    images.addAll(determineImages(project, extension))
                }
            }
        })
    }

    private Provider<Set<String>> determineImages(Project project, EXT extension) {
        project.provider(new Callable<Set<String>>() {
            @Override
            Set<String> call() throws Exception {
                if (extension.images.getOrNull()) {
                    return extension.images.get()
                }

                String tagVersion = project.version == 'unspecified' ? 'latest' : project.version
                String artifactAndVersion = "${project.name}:${tagVersion}".toLowerCase().toString()
                [project.group ? "$project.group/$artifactAndVersion".toString() : artifactAndVersion] as Set<String>
            }
        })
    }

    private static void registerPushImageTask(Project project, TaskProvider<DockerBuildImage> dockerBuildImageTask) {
        project.tasks.register(PUSH_IMAGE_TASK_NAME, DockerPushImage, new Action<DockerPushImage>() {
            @Override
            void execute(DockerPushImage pushImage) {
                pushImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Pushes created Docker image to the repository.'
                    dependsOn dockerBuildImageTask
                    images.convention(project.provider(new Callable<Set<String>>() {
                        @Override
                        Set<String> call() throws Exception {
                            dockerBuildImageTask.get().getImages().get()
                        }
                    }))
                }
            }
        })
    }

    private String getApplicationMainClassName(Project project, EXT extension) {
        if (extension.mainClassName.isPresent()) {
            return extension.mainClassName.get()
        }

        for (File classesDir : getMainJavaSourceSetOutput(project).classesDirs) {
            String mainClassName = findMainClassName(classesDir)

            if (mainClassName) {
                return mainClassName
            }
        }

        throw new IllegalStateException('Main class name could not be resolved')
    }

    protected abstract EXT configureExtension(ObjectFactory objectFactory, DockerExtension dockerExtension)
    protected abstract String findMainClassName(File classesDir)
}
