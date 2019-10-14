package com.bmuschko.gradle.docker.fixtures

final class DockerConventionPluginFixture {

    public static final String PROJECT_NAME = 'powered-by-docker'
    public static final String DEFAULT_BASE_IMAGE = 'openjdk:jre-alpine'
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
            
            task removeImage(type: DockerRemoveImage) {
                dependsOn dockerBuildImage
                targetImageId dockerBuildImage.getImageId()
                force = true
            }
            
            task buildAndRemoveImage {
                dependsOn dockerBuildImage
                finalizedBy removeImage
            }
            
            task pushAndRemoveImage {
                dependsOn dockerPushImage
                finalizedBy removeImage
            }
        """
    }

    static String containerTasks() {
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task createContainer(type: DockerCreateContainer) {
                dependsOn dockerBuildImage
                targetImageId dockerBuildImage.getImageId()
            }

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
            
            task stopContainer(type: DockerStopContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
            }
            
            task removeContainer(type: DockerRemoveContainer) {
                dependsOn stopContainer
                targetContainerId stopContainer.getContainerId()
            }
            
            task startAndRemoveContainer {
                dependsOn startContainer
                finalizedBy removeContainer
            }
        """
    }

    static String lifecycleTask() {
        """
            removeImage.mustRunAfter removeContainer

            task buildAndCleanResources {
                dependsOn startAndRemoveContainer
                finalizedBy removeImage
            }
        """
    }
}
