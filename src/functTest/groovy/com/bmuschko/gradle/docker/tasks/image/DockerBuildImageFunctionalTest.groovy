package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import org.gradle.testkit.runner.BuildResult
import spock.lang.Unroll

class DockerBuildImageFunctionalTest extends AbstractFunctionalTest {

    def "prints error message when image build fails"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage

            task dockerfile(type: Dockerfile) {
                from 'alpine'
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
                from 'alpine'
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
                from 'alpine'

                arg('arg1')
                arg('arg2')

                label(['label1':'\$arg1', 'label2':'\$arg2'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                buildArgs = ['arg1':'test1', 'arg2':'test2']
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
                from 'alpine'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                labels = ['label1':'test1', 'label2':'test2']
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

            task dockerfile(type: Dockerfile) {
                from 'alpine'
            }

            task buildImageWithTags(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tags = ['test/image:123', 'registry.com:5000/test/image:123']
            }
            
            task buildImageWithTag(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                tag = 'test/image:123'
            }
        """
    }
}
