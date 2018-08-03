package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class DockerBuildImageFunctionalTest extends AbstractFunctionalTest {

    def "prints error message when image build fails"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                addFile('./aaa', 'aaa')
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy removeImage
                inputDir = file("build/docker")
            }
        """

        when:
        BuildResult result = buildAndFail('buildImage')

        then:
        result.output.contains('aaa: no such file or directory')
    }

    def "can build image"() {
        buildFile << imageCreation()

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('Created image with ID')
    }

    @Unroll
    def "can build image with labels"(String gradleTaskDefinition) {
        buildFile << gradleTaskDefinition

        when:
        BuildResult result = build('inspectImage')

        then:
        result.output.contains('label1:test1, label2:test2')

        where:
        gradleTaskDefinition << [imageCreationWithBuildArgs(), imageCreationWithLabelParameter()]
    }

    @Unroll
    def "can build image with #description"() {
        buildFile << buildImageWithTags(tagsCommand)

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains(expectedOutput)

        where:
        tagsCommand | expectedOutput | description
        "tag = 'test/image:123'" | "Using tag 'test/image:123' for image." | 'single tag'
        "tags = ['test/image:123', 'registry.com:5000/test/image:123']" | "Using tags 'test/image:123', 'registry.com:5000/test/image:123' for image." | 'multiple tags'
    }


    def "can set /dev/shm size for image build"() {
        buildFile << buildImageWithShmSize()

        when:
        build('buildImage')

        then:
        noExceptionThrown()
    }

    private static String buildImageWithShmSize() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy removeImage
                inputDir = file("build/docker")
                shmSize = 128000
            }
        """
    }

    private static String imageCreation() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }

            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy removeImage
                inputDir = file("build/docker")
            }
        """
    }

    private static String imageCreationWithBuildArgs() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

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

            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }
            
            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                finalizedBy removeImage
                targetImageId { buildImage.getImageId() }
            }
        """
    }

    private static String imageCreationWithLabelParameter() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerInspectImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }
            
            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                labels = ['label1':'test1', 'label2':'test2', 'label3':"\$project.name"]
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task inspectImage(type: DockerInspectImage) {
                dependsOn buildImage
                finalizedBy removeImage
                targetImageId { buildImage.getImageId() }
            }
        """
    }

    private static String buildImageWithTags(String tagsCommand) {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
            }
            
            task removeImage(type: DockerRemoveImage) {
                targetImageId { buildImage.getImageId() }
                force = true
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                finalizedBy removeImage
                inputDir = file("build/docker")
                $tagsCommand
            }
        """
    }
}
