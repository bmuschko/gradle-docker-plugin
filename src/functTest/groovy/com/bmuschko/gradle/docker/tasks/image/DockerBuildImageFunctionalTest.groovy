package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import com.bmuschko.gradle.docker.TestConfiguration
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
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
                inputDir = file("build/docker")
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

    def "can build image with multiple tags"() {
        buildFile << buildImageWithTags()

        when:
        BuildResult result = build('buildImageWithTags', 'buildImageWithTag')

        then:
        result.output.contains("Using tags 'test/image:123', 'registry.com:5000/test/image:123' for image.")
        result.output.contains("Using tag 'test/image:123' for image.")
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

    private String buildImageWithShmSize() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine'
            }

            task buildWithShmSize(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                shmSize = 128000
            }
        """
    }

    private String imageCreation() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }
        """
    }

    private String imageCreationWithBuildArgs() {
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
                inputDir = file("build/docker")
                buildArgs = ['arg1':'test1', 'arg2':'test2', 'arg3': "\$project.name"]
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }
        """
    }

    private String imageCreationWithLabelParameter() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }
            
            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                labels = ['label1':'test1', 'label2':'test2', 'label3':"\$project.name"]
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                targetImageId { buildImage.getImageId() }
            }
        """
    }

    private String buildImageWithTags() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            project.version = "123"

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task buildImageWithTags(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tags = ['test/image:123', "registry.com:5000/test/image:\$project.version"]
            }
            
            task buildImageWithTag(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tag = 'test/image:123'
            }
        """
    }

    private String buildImageUsingCacheFromWithNothingInCache() {
        def uniqueImageId = createUniqueImageId()
        def uniqueTag = "${TestConfiguration.dockerPrivateRegistryDomain}/$uniqueImageId"
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            project.version = "123"

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                maintainer '${UUID.randomUUID().toString()}'
            }

            task buildImageWithCacheFrom(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                cacheFrom.add('$uniqueTag:latest')
                tag = '$uniqueTag:latest'
            }
        """
    }

    private String buildPushRemovePullBuildImage(boolean useCacheFrom) {
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
                maintainer '${UUID.randomUUID().toString()}'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = dockerfile.destFile.parentFile
                cacheFrom.add('$TEST_IMAGE_WITH_TAG') // no effect
                tag = '$uniqueTag'
            }

            task pushImage(type: DockerPushImage) {
                dependsOn buildImage
                conventionMapping.imageName = { buildImage.getTag() }
                tag = 'latest'
            }

            task removeImage(type: DockerRemoveImage) {
                dependsOn pushImage
                force = true
                targetImageId { buildImage.getImageId() }
            }

            task pullImage(type: DockerPullImage) {
                dependsOn removeImage
                repository = '$uniqueTag'
                tag = 'latest'
            }

            task buildImageWithCacheFrom(type: DockerBuildImage) {
                dependsOn pullImage
                inputDir = dockerfile.destFile.parentFile
                ${useCacheFrom ? "cacheFrom.add('$uniqueTag:latest')" : ""}
            }
        """
    }
}
