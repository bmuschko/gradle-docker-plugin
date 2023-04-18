package com.bmuschko.gradle.docker.fixtures

final class DockerConventionPluginFixture {

    public static final String PROJECT_NAME = 'powered-by-docker'
    public static final String DEFAULT_BASE_IMAGE = 'openjdk:11.0.16-jre-slim'
    public static final String CUSTOM_BASE_IMAGE = 'openjdk:8u171-jre-alpine'

    private DockerConventionPluginFixture() {}

    static String groovySettingsFile() {
        """
            rootProject.name = '$PROJECT_NAME'
        """
    }

    static String imageTasks() {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            def removeImage = tasks.register('removeImage', DockerRemoveImage) {
                dependsOn dockerBuildImage
                targetImageId dockerBuildImage.getImageId()
                force = true
            }

            def buildAndRemoveImage = tasks.register('buildAndRemoveImage') {
                dependsOn dockerBuildImage
                finalizedBy removeImage
            }

            def pushAndRemoveImage = tasks.register('pushAndRemoveImage') {
                dependsOn dockerPushImage
                finalizedBy removeImage
            }
        """
    }

    static String containerTasks() {
        """
            import org.gradle.api.Task
            import org.gradle.api.tasks.TaskProvider
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            TaskProvider<DockerCreateContainer> createContainer = tasks.register('createContainer', DockerCreateContainer) {
                dependsOn dockerBuildImage
                targetImageId dockerBuildImage.getImageId()
            }

            TaskProvider<DockerStartContainer> startContainer = tasks.register('startContainer', DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.get().getContainerId()
            }

            TaskProvider<DockerStopContainer> stopContainer = tasks.register('stopContainer', DockerStopContainer) {
                dependsOn startContainer
                targetContainerId startContainer.get().getContainerId()
            }

            TaskProvider<DockerRemoveContainer> removeContainer = tasks.register('removeContainer', DockerRemoveContainer) {
                dependsOn stopContainer
                targetContainerId stopContainer.get().getContainerId()
            }

            TaskProvider<Task> startAndRemoveContainer = tasks.register('startAndRemoveContainer') {
                dependsOn startContainer
                finalizedBy removeContainer
            }
        """
    }

    static String lifecycleTask() {
        """
            removeImage.configure {
                mustRunAfter removeContainer
            }

            tasks.register('buildAndCleanResources') {
                dependsOn startAndRemoveContainer
                finalizedBy removeImage
            }
        """
    }
}
