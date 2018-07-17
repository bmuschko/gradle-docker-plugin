package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import static com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin.SYNC_ARCHIVE_TASK_NAME
import static com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin.DOCKERFILE_TASK_NAME
import static com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin.BUILD_IMAGE_TASK_NAME
import static com.bmuschko.gradle.docker.DockerSpringBootApplicationPlugin.PUSH_IMAGE_TASK_NAME

class DockerSpringBootApplicationPluginIntegrationTest extends AbstractIntegrationTest {
    Project project

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    }

    def "Does not create tasks out-of-the-box when application plugin is not applied"() {
        when:
        applyDockerSpringBootApplicationPluginWithoutSpringBootPlugin(project)

        then:
        !project.tasks.findByName(SYNC_ARCHIVE_TASK_NAME)
        !project.tasks.findByName(DOCKERFILE_TASK_NAME)
        !project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        !project.tasks.findByName(PUSH_IMAGE_TASK_NAME)
    }

    def "Creates tasks out-of-the-box when Spring Boot plugin is applied"() {
        when:
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)

        then:
        project.tasks.findByName(SYNC_ARCHIVE_TASK_NAME)
        project.tasks.findByName(DOCKERFILE_TASK_NAME)
        project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        project.tasks.findByName(PUSH_IMAGE_TASK_NAME)
    }

    def "Configures image task without project group and version"() {
        when:
        project.apply(plugin: 'application')
        project.apply(plugin: DockerSpringBootApplicationPlugin)

        then:
        DockerBuildImage task = project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        task.tag == "${project.applicationName}:latest"
    }

    def "Configures image task without project group and but with version"() {
        given:
        String projectVersion = '1.0'

        when:
        project.version = projectVersion
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)

        then:
        DockerBuildImage task = project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        task.tag == "${project.applicationName}:${projectVersion}"
    }

    def "Configures image task with project group and version"() {
        given:
        String projectGroup = 'com.company'
        String projectVersion = '1.0'

        when:
        project.group = projectGroup
        project.version = projectVersion
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)

        then:
        DockerBuildImage task = project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        task.tag == "${projectGroup}/${project.applicationName}:${projectVersion}"
    }

    def "Can access the dockerJava.springBootApplication extension dynamically"() {
        given:
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)
        when:
        project.docker.springBootApplication
        then:
        noExceptionThrown()
    }

    @CompileStatic
    def "Can access the dockerJava.springBootApplication extension statically"() {
        given:
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)
        when:
        project.extensions.getByType(DockerExtension).springBootApplication
        then:
        noExceptionThrown()
    }

    @TypeChecked
    def "Can configure the dockerJava.springBootApplication extension statically"() {
        given:
        String testTagName = "some-test-tag"
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)
        when:
        project.extensions.getByType(DockerExtension).springBootApplication {
            // In kotlin this becomes much nicer.
            it.tag = "some-test-tag"
        }
        then:
        DockerBuildImage task = project
            .tasks
            .findByName(BUILD_IMAGE_TASK_NAME) as DockerBuildImage
        task.getTag() == testTagName
    }

    def "Can configure the dockerJava.springBootApplication extension dynamically"() {
        given:
        String testTagName = "some-test-tag"
        applyDockerSpringBootApplicationPluginAndSpringBootPlugin(project)
        when:
        project.docker.springBootApplication {
            tag = testTagName
        }
        then:
        DockerBuildImage task = project.tasks.findByName(BUILD_IMAGE_TASK_NAME)
        task.tag == testTagName
    }

    private void applyDockerSpringBootApplicationPluginWithoutSpringBootPlugin(Project project) {
        project.apply(plugin: DockerSpringBootApplicationPlugin)
    }

    private void applyDockerSpringBootApplicationPluginAndSpringBootPlugin(Project project) {
        project.buildscript {
            repositories {
                maven {
                    url "https://plugins.gradle.org/m2/"
                }
            }
            dependencies {
                classpath "org.springframework.boot:spring-boot-gradle-plugin:2.0.3.RELEASE"
            }
        }
        project.apply(plugin: "org.springframework.boot")

        applyDockerSpringBootApplicationPluginWithoutSpringBootPlugin(project)
    }
}
