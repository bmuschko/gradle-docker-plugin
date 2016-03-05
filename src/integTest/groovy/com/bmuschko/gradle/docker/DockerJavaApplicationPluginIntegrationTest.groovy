package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class DockerJavaApplicationPluginIntegrationTest extends AbstractIntegrationTest {
    Project project

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    }

    def "Does not create tasks out-of-the-box when application plugin is not applied"() {
        when:
        applyDockerJavaApplicationPluginWithoutApplicationPlugin(project)

        then:
        !project.tasks.findByName(DockerJavaApplicationPlugin.COPY_DIST_RESOURCES_TASK_NAME)
        !project.tasks.findByName(DockerJavaApplicationPlugin.DOCKERFILE_TASK_NAME)
        !project.tasks.findByName(DockerJavaApplicationPlugin.BUILD_IMAGE_TASK_NAME)
        !project.tasks.findByName(DockerJavaApplicationPlugin.PUSH_IMAGE_TASK_NAME)
    }

    def "Creates tasks out-of-the-box when application plugin is applied"() {
        when:
        applyDockerJavaApplicationPluginAndApplicationPlugin(project)

        then:
        project.tasks.findByName(DockerJavaApplicationPlugin.COPY_DIST_RESOURCES_TASK_NAME)
        project.tasks.findByName(DockerJavaApplicationPlugin.DOCKERFILE_TASK_NAME)
        project.tasks.findByName(DockerJavaApplicationPlugin.BUILD_IMAGE_TASK_NAME)
        project.tasks.findByName(DockerJavaApplicationPlugin.PUSH_IMAGE_TASK_NAME)
    }

    def "Configures image task without project group and version"() {
        when:
        project.apply(plugin: 'application')
        project.apply(plugin: DockerJavaApplicationPlugin)

        then:
        DockerBuildImage task = project.tasks.findByName(DockerJavaApplicationPlugin.BUILD_IMAGE_TASK_NAME)
        task.tag == "${project.applicationName}:latest"
    }

    def "Configures image task without project group and but with version"() {
        given:
        String projectVersion = '1.0'

        when:
        project.version = projectVersion
        applyDockerJavaApplicationPluginAndApplicationPlugin(project)

        then:
        DockerBuildImage task = project.tasks.findByName(DockerJavaApplicationPlugin.BUILD_IMAGE_TASK_NAME)
        task.tag == "${project.applicationName}:${projectVersion}"
    }

    def "Configures image task with project group and version"() {
        given:
        String projectGroup = 'com.company'
        String projectVersion = '1.0'

        when:
        project.group = projectGroup
        project.version = projectVersion
        applyDockerJavaApplicationPluginAndApplicationPlugin(project)

        then:
        DockerBuildImage task = project.tasks.findByName(DockerJavaApplicationPlugin.BUILD_IMAGE_TASK_NAME)
        task.tag == "${projectGroup}/${project.applicationName}:${projectVersion}"
    }

    private void applyDockerJavaApplicationPluginWithoutApplicationPlugin(Project project) {
        project.apply(plugin: DockerJavaApplicationPlugin)
    }

    private void applyDockerJavaApplicationPluginAndApplicationPlugin(Project project) {
        project.apply(plugin: 'application')
        applyDockerJavaApplicationPluginWithoutApplicationPlugin(project)
    }
}
