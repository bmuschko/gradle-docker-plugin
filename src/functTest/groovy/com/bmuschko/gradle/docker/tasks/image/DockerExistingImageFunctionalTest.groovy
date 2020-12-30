package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerExistingImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can refer to id of image via build task"() {
        buildFile << buildAndRemoveImage()
        buildFile << '''
            removeImage {
                dependsOn buildImage
            }
        '''

        when:
        BuildResult result = build('removeImage')

        then:
        result.task(':removeImage').outcome == TaskOutcome.SUCCESS
    }

    def "can refer to name of image via build task"() {
        buildFile << buildAndRemoveImage()
        buildFile << '''
            buildImage {
                images.add 'test/image:123'
            }
        '''

        when:
        BuildResult buildResult = build('buildImage')
        BuildResult removeResult = build('removeImage')

        then:
        buildResult.task(':buildImage').outcome == TaskOutcome.SUCCESS
        removeResult.task(':removeImage').outcome == TaskOutcome.SUCCESS
    }

    private static String buildAndRemoveImage() {
        """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'jane.doe@example.com'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId tasks.named('buildImage')
            }
        """
    }
}
