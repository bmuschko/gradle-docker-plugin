package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerPushImageFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "fails on push error"() {
        def image = createUniqueImageId()

        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from '$TEST_IMAGE_WITH_TAG'
                label(['maintainer': 'jane.doe@example.com'])
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
                images.add("${image}")
            }

            task removeImage(type: DockerRemoveImage) {
                 dependsOn buildImage
                 targetImageId "${image}"
                 force = true
            }

            task pushAndRemoveImage(type: DockerPushImage) {
                dependsOn buildImage
                images.add("${image}")
                finalizedBy removeImage
            }
        """

        when:
        BuildResult result = buildAndFail('pushAndRemoveImage')

        then:
        result.task(':pushAndRemoveImage').outcome == TaskOutcome.FAILED
        result.output.contains("Could not push image")
    }
}
