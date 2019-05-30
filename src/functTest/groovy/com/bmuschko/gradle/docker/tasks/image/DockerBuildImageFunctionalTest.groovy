package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.PendingFeature
import spock.lang.Requires
import spock.lang.Unroll

class DockerBuildImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "prints error message when image build fails"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                addFile('./aaa', 'aaa')
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

    def "can build image changing buildLabels without forcing to rebuild"() {
        buildFile << imageCreationWithInspection()

        when:
        buildFile << """
            buildImage {
                buildLabels = ['buildLabel1':'test1', 'buildLabel2':'test2']
            }
        """
        BuildResult result = build('inspectImage')

        then:
        result.output.contains("buildLabel1:test1, buildLabel2:test2")

        when:
        buildFile << """
            buildImage {
                buildLabels = ['buildLabel1':'test1', 'buildLabel2':'test2', 'buildLabel3':'test3']
            }
        """
        result = build('inspectImage')

        then:
        result.output.contains("buildLabel1:test1, buildLabel2:test2")
        !result.output.contains("buildLabel3:test3")

        when:
        buildFile << """
            buildImage {
                labels = ['label1':'test5', 'label2':'test6']
            }
        """
        result = build('inspectImage')

        then:
        result.output.contains("buildLabel1:test1, buildLabel2:test2")
        result.output.contains("buildLabel3:test3")
        result.output.contains("label1:test5, label2:test6")
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
        """
    }

    private static String imageCreation() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                runCommand('pwd')
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
            }
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
        """
    }

    private static String imageCreationWithInspection() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId buildImage.getImageId()
            }
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
        """
    }
}
