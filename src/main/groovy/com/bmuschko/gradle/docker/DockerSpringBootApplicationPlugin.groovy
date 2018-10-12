package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar

import java.util.concurrent.Callable

/**
 * @since 3.4.5
 */
@CompileStatic
class DockerSpringBootApplicationPlugin implements Plugin<Project> {
    public static final String SPRING_BOOT_APPLICATION_EXTENSION_NAME = 'springBootApplication'
    public static final String SYNC_ARCHIVE_TASK_NAME = 'dockerSyncArchive'
    public static final String DOCKERFILE_TASK_NAME = 'dockerCreateDockerfile'
    public static final String BUILD_IMAGE_TASK_NAME = 'dockerBuildImage'
    public static final String PUSH_IMAGE_TASK_NAME = 'dockerPushImage'
    private static final String BOOT_JAR_TASK_NAME = 'bootJar'
    private static final String BOOT_WAR_TASK_NAME = 'bootWar'

    @Override
    void apply(Project project) {
        project.plugins.apply(DockerRemoteApiPlugin)
        DockerExtension dockerExtension = project.extensions.getByType(DockerExtension)
        DockerSpringBootApplication dockerSpringBootApplication = configureExtension(project, dockerExtension)

        project.plugins.withId('org.springframework.boot') {
            Jar archiveTask = determineArchiveTask(project)
            Dockerfile createDockerfileTask = createDockerfileTask(project, archiveTask, dockerSpringBootApplication)
            Sync syncWarTask = createSyncArchiveTask(project, archiveTask, createDockerfileTask)
            createDockerfileTask.dependsOn syncWarTask
            DockerBuildImage dockerBuildImageTask = createBuildImageTask(project, createDockerfileTask, dockerSpringBootApplication)
            createPushImageTask(project, dockerBuildImageTask)
        }
    }

    private static Jar determineArchiveTask(Project project) {
        Task bootWarTask = project.tasks.findByName(BOOT_WAR_TASK_NAME)
        Task archiveTask = bootWarTask ?: project.tasks.getByName(BOOT_JAR_TASK_NAME)
        archiveTask as Jar
    }

    private static DockerSpringBootApplication configureExtension(Project project, DockerExtension dockerExtension) {
        ((ExtensionAware) dockerExtension).extensions.create(SPRING_BOOT_APPLICATION_EXTENSION_NAME, DockerSpringBootApplication, project)
    }

    private Sync createSyncArchiveTask(Project project, Jar archiveTask, Dockerfile createDockerfileTask) {
        project.tasks.create(SYNC_ARCHIVE_TASK_NAME, Sync, new Action<Sync>() {
            @Override
            void execute(Sync sync) {
                sync.group = DockerRemoteApiPlugin.DEFAULT_TASK_GROUP
                sync.description = "Copies the distribution resources to a temporary directory for image creation."
                sync.dependsOn project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME)
                sync.from { archiveTask.archivePath }
                sync.into { createDockerfileTask.destFile.get().asFile.parentFile }
            }
        })
    }

    private Dockerfile createDockerfileTask(Project project, Jar archiveTask, DockerSpringBootApplication dockerSpringBootApplication) {
        project.tasks.create(DOCKERFILE_TASK_NAME, Dockerfile, new Action<Dockerfile>() {
            @Override
            void execute(Dockerfile dockerfile) {
                dockerfile.description = 'Creates the Docker image for the Spring Boot application.'
                dockerfile.dependsOn archiveTask
                dockerfile.from(project.provider(new Callable<Dockerfile.From>() {
                    @Override
                    Dockerfile.From call() throws Exception {
                        new Dockerfile.From(dockerSpringBootApplication.baseImage.get())
                    }
                }))
                dockerfile.copyFile(project.provider(new Callable<Dockerfile.File>() {
                    @Override
                    Dockerfile.File call() throws Exception {
                        new Dockerfile.File(archiveTask.archiveName, "/app/${archiveTask.archiveName}".toString())
                    }
                }))
                dockerfile.entryPoint('java')
                dockerfile.defaultCommand(project.provider(new Callable<List<String>>() {
                    @Override
                    List<String> call() throws Exception {
                        ['-jar', "/app/${archiveTask.archiveName}".toString()] as List<String>
                    }
                }))
                dockerfile.exposePort(dockerSpringBootApplication.ports)
            }
        })
    }

    private DockerBuildImage createBuildImageTask(Project project, Dockerfile createDockerfileTask, DockerSpringBootApplication dockerSpringBootApplication) {
        project.tasks.create(BUILD_IMAGE_TASK_NAME, DockerBuildImage, new Action<DockerBuildImage>() {
            @Override
            void execute(DockerBuildImage dockerBuildImage) {
                dockerBuildImage.description = 'Builds the Docker image for the Spring Boot application.'
                dockerBuildImage.dependsOn createDockerfileTask
                dockerBuildImage.inputDir.set(project.provider(new Callable<Directory>() {
                    @Override
                    Directory call() throws Exception {
                        project.layout.projectDirectory.dir(createDockerfileTask.destFile.get().asFile.parentFile.canonicalPath)
                    }
                }))
                dockerBuildImage.tag.set(determineImageTag(project, dockerSpringBootApplication))
            }
        })
    }

    private Provider<String> determineImageTag(Project project, DockerSpringBootApplication dockerSpringBootApplication) {
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

    private void createPushImageTask(Project project, DockerBuildImage dockerBuildImageTask) {
        project.tasks.create(PUSH_IMAGE_TASK_NAME, DockerPushImage, new Action<DockerPushImage>() {
            @Override
            void execute(DockerPushImage dockerPushImage) {
                dockerPushImage.description = 'Pushes created Docker image to the repository.'
                dockerPushImage.dependsOn dockerBuildImageTask
                dockerPushImage.imageName = dockerBuildImageTask.getTag()
            }
        })
    }
}
