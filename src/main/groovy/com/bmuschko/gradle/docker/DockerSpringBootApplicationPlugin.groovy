package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.bmuschko.gradle.docker.utils.MainClassFinder
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync

import java.util.concurrent.Callable

import static com.bmuschko.gradle.docker.utils.ConventionPluginHelper.createAppFilesCopySpec
import static com.bmuschko.gradle.docker.utils.ConventionPluginHelper.getMainJavaSourceSetOutput

/**
 * Opinionated Gradle plugin for creating and pushing a Docker image for a Spring Boot application.
 * <p>
 * This plugin can be configured with the help of {@link DockerSpringBootApplication}.
 *
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplicationPlugin implements Plugin<Project> {

    private static final String SPRING_BOOT_PLUGIN_ID = 'org.springframework.boot'
    private static final String SPRING_BOOT_APP_ANNOTATION = 'org.springframework.boot.autoconfigure.SpringBootApplication'

    /**
     * The name of extension registered with type {@link DockerSpringBootApplication}.
     */
    public static final String SPRING_BOOT_APPLICATION_EXTENSION_NAME = 'springBootApplication'

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
        DockerSpringBootApplication dockerSpringBootApplication = configureExtension(project.objects, dockerExtension)

        project.plugins.withType(JavaPlugin) {
            project.plugins.withId(SPRING_BOOT_PLUGIN_ID) {
                Dockerfile createDockerfileTask = createDockerfileTask(project, dockerSpringBootApplication)
                Sync syncBuildContextTask = createSyncBuildContextTask(project, createDockerfileTask)
                createDockerfileTask.dependsOn syncBuildContextTask
                DockerBuildImage dockerBuildImageTask = createBuildImageTask(project, createDockerfileTask, dockerSpringBootApplication)
                createPushImageTask(project, dockerBuildImageTask)
            }
        }
    }

    private static DockerSpringBootApplication configureExtension(ObjectFactory objectFactory, DockerExtension dockerExtension) {
        ((ExtensionAware) dockerExtension).extensions.create(SPRING_BOOT_APPLICATION_EXTENSION_NAME, DockerSpringBootApplication, objectFactory)
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
                    with(createAppFilesCopySpec(project))
                }
            }
        })
    }

    private static Dockerfile createDockerfileTask(Project project, DockerSpringBootApplication dockerSpringBootApplication) {
        project.tasks.create(DOCKERFILE_TASK_NAME, Dockerfile, new Action<Dockerfile>() {
            @Override
            void execute(Dockerfile dockerfile) {
                dockerfile.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Creates the Docker image for the Spring Boot application.'
                    from(project.provider(new Callable<Dockerfile.From>() {
                        @Override
                        Dockerfile.From call() throws Exception {
                            new Dockerfile.From(dockerSpringBootApplication.baseImage.get())
                        }
                    }))
                    label(project.provider(new Callable<Map<String, String>>() {
                        @Override
                        Map<String, String> call() throws Exception {
                            ['maintainer': dockerSpringBootApplication.maintainer.get()]
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
                            List<String> jvmArgs = dockerSpringBootApplication.jvmArgs.get()

                            if (!jvmArgs.empty) {
                                entrypoint.addAll(jvmArgs)
                            }

                            entrypoint.addAll(["-cp", "/app/resources:/app/classes:/app/libs/*", getSpringApplicationMainClassName(project)])
                            entrypoint
                        }
                    }))
                    exposePort(dockerSpringBootApplication.ports)
                }
            }
        })
    }

    private static DockerBuildImage createBuildImageTask(Project project, Dockerfile createDockerfileTask, DockerSpringBootApplication dockerSpringBootApplication) {
        project.tasks.create(BUILD_IMAGE_TASK_NAME, DockerBuildImage, new Action<DockerBuildImage>() {
            @Override
            void execute(DockerBuildImage dockerBuildImage) {
                dockerBuildImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Builds the Docker image for the Spring Boot application.'
                    dependsOn createDockerfileTask
                    tags.add(determineImageTag(project, dockerSpringBootApplication))
                }
            }
        })
    }

    private static Provider<String> determineImageTag(Project project, DockerSpringBootApplication dockerSpringBootApplication) {
        project.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                if (dockerSpringBootApplication.tag.getOrNull()) {
                    return dockerSpringBootApplication.tag.get()
                }

                String tagVersion = project.version == 'unspecified' ? 'latest' : project.version
                String artifactAndVersion = "${project.name}:${tagVersion}".toLowerCase().toString()
                project.group ? "$project.group/$artifactAndVersion".toString() : artifactAndVersion
            }
        })
    }

    private static void createPushImageTask(Project project, DockerBuildImage dockerBuildImageTask) {
        project.tasks.create(PUSH_IMAGE_TASK_NAME, DockerPushImage, new Action<DockerPushImage>() {
            @Override
            void execute(DockerPushImage dockerPushImage) {
                dockerPushImage.with {
                    group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                    description = 'Pushes created Docker image to the repository.'
                    dependsOn dockerBuildImageTask
                    imageName.set(dockerBuildImageTask.getTags().map(new Transformer<String, Set<String>>() {
                        @Override
                        String transform(Set<String> tags) {
                            tags.first()
                        }
                    }))
                }
            }
        })
    }

    private static String getSpringApplicationMainClassName(Project project) {
        for (File classesDir : getMainJavaSourceSetOutput(project).classesDirs) {
            String mainClassName = MainClassFinder.findSingleMainClass(classesDir, SPRING_BOOT_APP_ANNOTATION)

            if (mainClassName) {
                return mainClassName
            }
        }

        throw new IllegalStateException('Main class name could not be resolved')
    }
}
