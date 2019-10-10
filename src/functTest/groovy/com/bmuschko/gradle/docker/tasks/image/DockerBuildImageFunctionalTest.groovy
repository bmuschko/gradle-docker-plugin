package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Unroll

class DockerBuildImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "prints error message when image build fails"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                addFile(new Dockerfile.File('./aaa', 'aaa'))
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
            }
        """

        when:
        def result = buildAndFail('buildImage')

        then:
        result.output.contains("aaa: no such file or directory")
    }

    def "can build image"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains("Created image with ID")
    }

    def "can build image in a parent context"() {
        buildFile << imageCreation()
        buildFile << """
            buildImage {
                inputDir = projectDir
                dockerFile = dockerfile.destFile
            }
        """

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains("Created image with ID")
    }

    @Unroll
    def "can build image with labels"(String gradleTaskDefinition) {
        buildFile << gradleTaskDefinition

        when:
        BuildResult result = build('inspectImage')

        then:
        result.output.contains("label1:test1, label2:test2")

        where:
        gradleTaskDefinition << [imageCreationWithBuildArgs(), imageCreationWithLabelParameter()]
    }

    def "labels can be excluded from up-to-date check"() {
        given:
        buildFile << dockerFile() << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task buildImage(type: CustomDockerBuildImage) {
                dependsOn dockerfile
                labels = ["build-date": "\${getBuildDate()}"]
            }

            def getBuildDate() {
                return new Date().format('yyyyMMddHHmmss.SSS')
            }

            class CustomDockerBuildImage extends DockerBuildImage {
                @Override
                @Internal
                MapProperty<String, String> getLabels() {
                    super.getLabels()
                }
            }
        """

        when:
        BuildResult result = build('buildImage')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS

        when:
        result = build('buildImage')

        then:
        result.task(':buildImage').outcome == TaskOutcome.UP_TO_DATE
    }

    def "can build image with multiple tags"() {
        buildFile << buildImageWithTags()

        when:
        BuildResult result = build('buildImageWithTags', 'buildImageWithTag')

        then:
        result.output.contains("Using tags 'test/image:123', 'registry.com:5000/test/image:123' for image.")
        result.output.contains("Using tags 'test/image:123' for image.")
    }


    def "can set /dev/shm size for image build"() {
        buildFile << buildImageWithShmSize()

        when:
        build("buildWithShmSize")

        then:
        noExceptionThrown()
    }

    def "can build image with different targets"() {
        buildFile << buildMultiStageImage()

        when:
        BuildResult result = build('buildTarget')

        then:
        // check the output for the built stages
        result.output.contains('Step 2/4 : LABEL maintainer=stage1')
        result.output.contains('Step 4/4 : LABEL maintainer=stage2')
        // stage3 was not called
        ! result.output.contains('stage3')
    }

    def "can build image using --cache-from with nothing in the cache"() {
        buildFile << buildImageUsingCacheFromWithNothingInCache()

        when:
        BuildResult result = build('buildImageWithCacheFrom')

        then:
        result.output.contains("Successfully built")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "cache is not used for build without --cached-from"() {
        buildFile << buildPushRemovePullBuildImage(false)

        when:
        BuildResult result = build('buildImageWithCacheFrom')

        then:
        !result.output.contains("Using cache")
    }

    @Requires({ TestPrecondition.DOCKER_PRIVATE_REGISTRY_REACHABLE })
    def "cache is used for build using --cached-from"() {
        buildFile << buildPushRemovePullBuildImage(true)

        when:
        BuildResult result = build('buildImageWithCacheFrom')

        then:
        result.output.contains("Using cache")
    }

    def "can build image using --network host"() {
        buildFile << buildImageWithHostNetwork()

        when:
        build('buildWithHostNetwork')

        then:
        noExceptionThrown()
    }

    def "task can be up-to-date"() {
        given:
        buildFile << imageCreation()
        buildFile << """
            task verifyImageId {
                dependsOn buildImage
            }
        """
        File imageIdFile = new File(projectDir, 'build/.docker/buildImage-imageId.txt')

        when:
        BuildResult result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""
    }

    def "try to reproduce"() {
        given:

        TemporaryFolder temporaryFolder = new TemporaryFolder()
        temporaryFolder.create()
        def temporaryDir = temporaryFolder.root
        def gradlebuildfile = temporaryFolder.newFile("build.gradle.kts")
        def dockerfile = temporaryFolder.newFile("Dockerfile")

        gradlebuildfile << """
import com.bmuschko.gradle.docker.tasks.image.*

plugins {
    id("com.bmuschko.docker-remote-api") version "5.1.0"
}

group = "edu.vanderbilt.isis"
version = "0.1-SNAPSHOT"

repositories {
    jcenter()
}

val docker_repository = "build_tensorflow"
val docker_tag = "latest"
val image_name_list = listOf(
        listOf(docker_repository, docker_tag).joinToString(":")
)

tasks.register<DockerBuildImage>("buildTensorflow") {

    inputDir.set(File("."))
    tags.set(image_name_list)
}
"""

        dockerfile << """
FROM tensorflow/tensorflow:1.13.1-gpu

RUN sh -c 'echo "deb http://packages.ros.org/ros/ubuntu \$(lsb_release -sc) main" > /etc/apt/sources.list.d/ros-latest.list'
"""

        def runner = GradleRunner.create()
            .withProjectDir(temporaryDir)
            .withArguments(["buildTensorflow", "-s"])
            .withPluginClasspath()

        when:
        BuildResult result = runner.build()

        then:
        result.task(':buildTensorflow').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")

        when:
        result = runner.build()

        then:
        result.task(':buildTensorflow').outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains("Created image with ID")
    }

    def "task not up-to-date when no imageIdFile"() {
        given:
        buildFile << imageCreation()
        buildFile << """
            task verifyImageId {
                dependsOn buildImage
            }
        """
        File imageIdFile = new File(projectDir, 'build/.docker/buildImage-imageId.txt')

        when:
        BuildResult result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        imageIdFile.delete()
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""
    }

    def "task not up-to-date when image not in registry"() {
        given:
        buildFile << imageCreation()
        buildFile << """
            task verifyImageId {
                dependsOn buildImage
            }
        """
        File imageIdFile = new File(projectDir, 'build/.docker/buildImage-imageId.txt')

        when:
        BuildResult result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        buildFile << imageRemoval(imageIdFile.text)
        result = build('removeImage')

        then:
        result.task(':removeImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Removing image with ID")

        when:
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""
    }

    @Issue('https://github.com/bmuschko/gradle-docker-plugin/issues/818')
    def "task not up-to-date when imageIdFile changed"() {
        given:
        buildFile << imageCreation()
        buildFile << """
            task verifyImageId {
                dependsOn buildImage
            }
        """
        File imageIdFile = new File(projectDir, 'build/.docker/buildImage-imageId.txt')

        when:
        BuildResult result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.UP_TO_DATE
        !result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""

        when:
        imageIdFile << " "
        result = build('verifyImageId')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains("Created image with ID")
        imageIdFile.isFile()
        imageIdFile.text != ""
    }

    private static String buildImageWithShmSize() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine'
            }

            task buildWithShmSize(type: DockerBuildImage) {
                dependsOn dockerfile
                shmSize = 128000L
            }

            ${imageIdValidation()}
        """
    }

    private static String dockerFile() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                runCommand("echo ${UUID.randomUUID()}")
            }
        """
    }


    private static String imageCreation() {
        dockerFile() << """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
            }

            ${imageIdValidation()}
        """
    }

    private static String imageRemoval(String imageId) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId '$imageId'
            }
        """
    }

    private static String imageCreationWithBuildArgs() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'

                arg('arg1')
                arg('arg2')

                label(['label1':'\$arg1', 'label2':'\$arg2'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                buildArgs = ['arg1':'test1', 'arg2':'test2', 'arg3': "\$project.name"]
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
            }

            ${imageIdValidation()}
        """
    }

    private static String imageCreationWithLabelParameter() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                labels = ['label1':'test1', 'label2':'test2', 'label3':"\$project.name"]
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
            }

            ${imageIdValidation()}
        """
    }

    private static String buildImageWithTags() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            project.version = "123"

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImageWithTags(type: DockerBuildImage) {
                dependsOn dockerfile
                tags = ['test/image:123', "registry.com:5000/test/image:\$project.version"]
            }

            task buildImageWithTag(type: DockerBuildImage) {
                dependsOn dockerfile
                tags.add('test/image:123')
            }

            ${imageIdValidation()}
        """
    }

    private static String buildImageUsingCacheFromWithNothingInCache() {
        def uniqueImageId = createUniqueImageId()
        def uniqueTag = "${TestConfiguration.dockerPrivateRegistryDomain}/$uniqueImageId"
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            project.version = "123"

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': '${UUID.randomUUID().toString()}'])
            }

            task buildImageWithCacheFrom(type: DockerBuildImage) {
                dependsOn dockerfile
                cacheFrom.add('$uniqueTag:latest')
                tags.add('$uniqueTag:latest')
            }

            ${imageIdValidation()}
        """
    }

    private static String buildPushRemovePullBuildImage(boolean useCacheFrom) {
        def uniqueImageId = createUniqueImageId()
        def uniqueTag = "${TestConfiguration.dockerPrivateRegistryDomain}/$uniqueImageId"
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            project.version = "123"

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': '${UUID.randomUUID().toString()}'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                cacheFrom.add('$TEST_IMAGE_WITH_TAG') // no effect
                tags.add('$uniqueTag')
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                imageName = buildImage.tags.get().first()
                tag = 'latest'
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn pushImage
                force = true
                targetImageId buildImage.getImageId()
            }

            task pullImage(type: DockerPullImage) {
                dependsOn removeImage
                repository = '$uniqueTag'
                tag = 'latest'
            }

            task buildImageWithCacheFrom(type: DockerBuildImage) {
                dependsOn pullImage
                inputDir = dockerfile.destFile.get().asFile.parentFile
                ${useCacheFrom ? "cacheFrom.add('$uniqueTag:latest')" : ""}
            }

            ${imageIdValidation()}
        """
    }

    private static String buildImageWithHostNetwork() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildWithHostNetwork(type: DockerBuildImage) {
                dependsOn dockerfile
                network = 'host'
            }

            ${imageIdValidation()}
        """
    }

    private static String buildMultiStageImage() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from(new Dockerfile.From('$TEST_IMAGE_WITH_TAG').withStage('stage1'))
                label(['maintainer': 'stage1'])

                from(new Dockerfile.From('$TEST_IMAGE_WITH_TAG').withStage('stage2'))
                label(['maintainer': 'stage2'])

                from(new Dockerfile.From('$TEST_IMAGE_WITH_TAG').withStage('stage3'))
                label(['maintainer': 'stage3'])
            }

            task buildTarget(type: DockerBuildImage) {
                dependsOn dockerfile
                target = "stage2"
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId buildTarget.getImageId()
            }

            buildTarget.finalizedBy tasks.removeImage

            ${imageIdValidation()}
        """
    }

    private static String imageIdValidation() {
        """
            tasks.withType(DockerBuildImage) { Task task ->
                def assertTask = tasks.create("assertImageIdFor\${task.name.capitalize()}"){
                    def dependantTask = task
                    doLast {
                        def value = dependantTask.getImageId().getOrNull()
                        if(value == null || !(value ==~ /^\\w+\$/)) {
                            throw new GradleException("The imageId property was not set from task \${dependantTask.name}")
                        }
                    }
                }

                task.finalizedBy assertTask
            }
        """
    }
}
