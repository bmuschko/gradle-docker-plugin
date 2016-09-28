package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.gradle.api.Project
import org.gradle.api.internal.file.UnionFileCollection
import org.gradle.testfixtures.ProjectBuilder

class SimpleDockerApplicationPluginTest extends AbstractIntegrationTest {
    Project project

    def setup() {
        project = ProjectBuilder.builder().withProjectDir(projectDir).build()
    }

    def "createAllTasks creates the dockerfile task, buildImage and pushAllImages"() {
        def x = Spy(SimpleDockerApplicationPlugin)

        when:
        x.createAllTasks project

        then:
        1 * x.taskCreateDockerfile(project) >> {}
        1 * x.taskBuildImage(project) >> {}
        1 * x.taskPushImage(project) >> {}
    }

    def "taskBuildImage creates the task and set it to depend on createDockerfile"() {
        def x = new SimpleDockerApplicationPlugin()
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }

        def mockParent = Mock(File, constructorArgs: ['/fake/parent'])

        def mockFile = Stub(File, constructorArgs: ['/fake/path']) {
            getParentFile() >> mockParent
        }
        def dockerFileTask = project.tasks.create('createDockerfile', Dockerfile)
        dockerFileTask.destFile = mockFile

        when:
        x.taskBuildImage(project)
        def task = project.tasks['buildImage']

        then:
        task.dependsOn.size() == 2
        task.dependsOn.find({ it.hasProperty('name') && it.name == 'createDockerfile' })
        task.inputDir.is(mockParent)
    }

    def "taskCreateDockerfile uses the property from SimpleDockerConfig and sets entry point and sets up right dependencies"() {
        def x = new SimpleDockerApplicationPlugin()
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'

        project.simpleDockerConfig.dockerBase = 'base docker'
        project.simpleDockerConfig.maintainerEmail = 'some email'
        String appLatest = "/${project.applicationName}-latest"
        String appDir = "/${project.applicationName}-${project.version}"

        when:
        def task = x.taskCreateDockerfile(project)

        then:
        task.dependsOn.find { !(it instanceof UnionFileCollection) && (it.name == 'distTar') }
        task.dependsOn.find { !(it instanceof UnionFileCollection) && (it.name == 'dockerCopyDistResources') }
        task.destFile.absolutePath.contains('/build/docker/Dockerfile')
        task.instructions.find { it instanceof Dockerfile.FromInstruction && it.command == 'base docker' }
        task.instructions.find { it instanceof Dockerfile.MaintainerInstruction && it.command == 'some email' }
        task.instructions.find { it instanceof Dockerfile.FileInstruction && (it.src == project.distTar.archiveName && it.dest == '/') }
        task.instructions.find { it instanceof Dockerfile.RunCommandInstruction && it.command == "ln -s '${appDir}' '${appLatest}'" }
        task.instructions.find { it instanceof Dockerfile.EntryPointInstruction && it.command == ["${appLatest}/bin/xyz"] }
    }

    def "taskCreateDockerfile also invokes the dockerImage closure if set"() {
        def x = new SimpleDockerApplicationPlugin()
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        project.configure(project) {
            apply plugin: 'application'
            apply plugin: 'distribution'
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.applicationName = 'xyz'

        project.simpleDockerConfig.dockerBase = 'base docker'
        project.simpleDockerConfig.maintainerEmail = 'some email'
        def calls = 0
        project.simpleDockerConfig.dockerImage = { project, task ->
            calls++
        }
        String appLatest = "/${project.applicationName}-latest"
        String appDir = "/${project.applicationName}-${project.version}"

        when:
        def task = x.taskCreateDockerfile(project)

        then:
        task.dependsOn.find { !(it instanceof UnionFileCollection) && (it.name == 'distTar') }
        task.dependsOn.find { !(it instanceof UnionFileCollection) && (it.name == 'dockerCopyDistResources') }
        task.destFile.absolutePath.contains('/build/docker/Dockerfile')
        task.instructions.find { it instanceof Dockerfile.FromInstruction && it.command == 'base docker' }
        task.instructions.find { it instanceof Dockerfile.MaintainerInstruction && it.command == 'some email' }
        task.instructions.find { it instanceof Dockerfile.FileInstruction && (it.src == project.distTar.archiveName && it.dest == '/') }
        task.instructions.find { it instanceof Dockerfile.RunCommandInstruction && it.command == "ln -s '${appDir}' '${appLatest}'" }
        task.instructions.find { it instanceof Dockerfile.EntryPointInstruction && it.command == ["${appLatest}/bin/xyz"] }
        calls == 1
    }

    def "taskPushImage creates task for tag and for push"() {
        def repoUrl = 'xyz.com:7001'
        def x = new SimpleDockerApplicationPlugin()
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.tasks.create 'buildImage'
        project.version = 'a.b.c'
        project.simpleDockerConfig.dockerRepo = repoUrl

        when:
        x.taskPushImage project
        def tagTest = project.tasks['dockerTagImage']
        def pushTest = project.tasks['pushImage']

        then:
        tagTest
        tagTest.dependsOn.size() == 2
        tagTest.dependsOn.find({ it.hasProperty('name') && it.name == 'buildImage' })
        tagTest.repository == repoUrl
        tagTest.getTag() == 'a.b.c'

        pushTest
        pushTest.dependsOn.size() == 2
        pushTest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImage' })
    }

    def "taskPushImage uses the version closure if supplied"() {
        def repoUrl = 'xyz.com:7001'
        def x = new SimpleDockerApplicationPlugin()
        project.extensions.create("simpleDockerConfig", SimpleDockerConfig)
        project.configure(project) {
            apply plugin: 'com.bmuschko.docker-java-application'
        }
        project.tasks.create 'buildImage'
        project.version = 'a.b.c'
        project.simpleDockerConfig.dockerRepo = repoUrl
        def calls = 0
        project.simpleDockerConfig.tagVersion = {
            calls++
            'x.y.z'
        }

        when:
        x.taskPushImage project
        def tagTest = project.tasks['dockerTagImage']
        def pushTest = project.tasks['pushImage']

        then:
        calls == 1
        
        tagTest
        tagTest.dependsOn.size() == 2
        tagTest.dependsOn.find({ it.hasProperty('name') && it.name == 'buildImage' })
        tagTest.repository == repoUrl
        tagTest.getTag() == 'x.y.z'

        pushTest
        pushTest.dependsOn.size() == 2
        pushTest.dependsOn.find({ it.hasProperty('name') && it.name == 'dockerTagImage' })
    }
}
