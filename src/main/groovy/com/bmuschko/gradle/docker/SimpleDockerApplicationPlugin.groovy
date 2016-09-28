package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.DockerTagImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Plugin that is meant to provide a very simple approach to packaging an application into a docker image.
 * It offers a simple interface to performing the following steps:
 * <ol>
 *  <li>Create the image (and allows to easily add files and execute commands)</li>
 *  <li>Tag the image in the docker repo (and allows to supply your own tag)</li>
 *  <li>Push the image to the docker repo</li>
 * </ol>
 *
 * @author liviutudor
 */
class SimpleDockerApplicationPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        def dockerConfig = project.simpleDockerConfig

        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }

        project.afterEvaluate {
            project.docker {
                url = dockerConfig.dockerUrl
                javaApplication {
                    baseImage = dockerConfig.dockerBase
                    maintainer = dockerConfig.maintainerEmail
                }
            }

            createAllTasks project
        }
    }

    protected void createAllTasks(Project project) {
        taskCreateDockerfile project
        taskBuildImage project
        taskPushImage project
    }

    protected Task taskCreateDockerfile(Project project) {
        String appLatest = "/${project.applicationName}-latest"
        String appDir = "/${project.applicationName}-${project.version}"
        project.tasks.create(name: 'createDockerfile', type: Dockerfile) { task ->
            destFile = project.file('./build/docker/Dockerfile')
            dependsOn project.tasks['distTar']
            dependsOn project.tasks['dockerCopyDistResources']
            from "${project.simpleDockerConfig.dockerBase}"
            maintainer project.simpleDockerConfig.maintainerEmail

            addFile "${project.distTar.archiveName}", '/'
            runCommand "ln -s '${appDir}' '${appLatest}'"
            entryPoint "${appLatest}/bin/${project.applicationName}"
            if (project.simpleDockerConfig.dockerImage) {
                project.simpleDockerConfig.dockerImage.delegate = task
                project.simpleDockerConfig.dockerImage(project, task)
            }
        }
    }

    protected Task taskBuildImage(Project project) {
        project.tasks.create(name: 'buildImage', type: DockerBuildImage) {
            dependsOn project.tasks['createDockerfile']
            inputDir = project.tasks['createDockerfile'].destFile.parentFile
        }
    }

    protected void taskPushImage(Project project) {
        SimpleDockerConfig dockerConfig = project.simpleDockerConfig

        String taggingVersion = ""
        if (dockerConfig.tagVersion) {
            dockerConfig.tagVersion.delegate = project
            taggingVersion = dockerConfig.tagVersion(project)
        } else {
            taggingVersion = "${project.version}".toString()
        }

        project.tasks.create(name: "dockerTagImage", type: DockerTagImage) { task ->
            dependsOn project.tasks['buildImage']
            targetImageId { project.buildImage.imageId }
            repository = dockerConfig.dockerRepo
            task.conventionMapping.tag = { taggingVersion }
            force = true
        }

        project.tasks.create(name: "pushImage", type: DockerPushImage) { task ->
            dependsOn project.tasks["dockerTagImage"]
            task.conventionMapping.imageName = { project.tasks["dockerTagImage"].getRepository() }
            task.conventionMapping.tag = { project.tasks["dockerTagImage"].getTag() }
        }
    }
}

/**
 * Simple bean used for holding configuration information for the {@link SimpleDockerApplicationPlugin}.
 *
 * @author liviutudor
 */
class SimpleDockerConfig {
    /**
     * Email address of the maintainer of the docker image.
     */
    def String maintainerEmail

    /**
     * Docker daemon url.
     */
    def String dockerUrl

    /**
     * Docker base image.
     */
    def String dockerBase

    /**
     * Docker repository URL.
     */
    def String dockerRepo

    /**
     * Closure to execute when building the docker image.
     * If you need any other files or symlinks or commands to be executed, specify them here.
     * If not set (or set to <code>null</code>) then no extra commands will be added to the Dockerfile.
     */
    def Closure dockerImage

    /**
     * Closure used to set the tag on the docker image.
     * Typically the code will set the tag to be the application version.
     * This closure allows you to define the tagging for the application version.
     * If not set, the application version will be set, as per above.
     */
    def Closure<String> tagVersion


    @Override
    public String toString() {
        return "SimpleDockerConfig{" +
                "maintainerEmail='" + maintainerEmail + '\'' +
                ", dockerUrl='" + dockerUrl + '\'' +
                ", dockerBase='" + dockerBase + '\'' +
                ", dockerRepo='" + dockerRepo + '\'' +
                ", dockerImage=" + dockerImage +
                ", tagVersion=" + tagVersion +
                '}';
    }
}
