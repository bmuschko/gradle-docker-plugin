package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerBuildImageBuildKitFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def setup() {
        new File(projectDir, 'build-files').mkdirs()
        new File(projectDir, 'build-files/mounted') << 'mounted file contents'
        new File(projectDir, 'Dockerfile') << """FROM $TEST_IMAGE_WITH_TAG
RUN --mount=type=bind,source=/build-files,target=/mnt cp /mnt/mounted /mounted
"""
    }

    def "can build an image that only BuildKit understands"() {
        buildFile << buildImageTask('buildKit = true')

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('Created image with ID')
    }

    def "the classic builder still rejects a BuildKit Dockerfile"() {
        buildFile << buildImageTask('')

        when:
        BuildResult result = buildAndFail('buildImage')

        then:
        result.output.contains('the --mount option requires BuildKit')
    }

    def "the extension turns BuildKit on for every image build"() {
        buildFile << """
            docker {
                buildKit = true
            }
        """ + buildImageTask('')

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('Created image with ID')
    }

    def "a task can opt out of the BuildKit the extension turned on"() {
        buildFile << """
            docker {
                buildKit = true
            }
        """ + buildImageTask('buildKit = false')

        when:
        BuildResult result = buildAndFail('buildImage')

        then:
        result.output.contains('the --mount option requires BuildKit')
    }

    def "warns about configured options BuildKit does not act on"() {
        buildFile << buildImageTask('''buildKit = true
                memory = 536870912
                remove = true''')

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('BuildKit does not act on the following configured options')
        result.output.contains('memory')
        result.output.contains('remove')
        result.output.contains('Created image with ID')
    }

    def "warns about ignored options when BuildKit is turned on by the extension"() {
        buildFile << """
            docker {
                buildKit = true
            }
        """ + buildImageTask('''memory = 536870912
                remove = true''')

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('BuildKit does not act on the following configured options')
        result.output.contains('Created image with ID')
    }

    def "turning BuildKit on in the extension reruns a build that was up to date"() {
        given:
        new File(projectDir, 'Dockerfile').text = "FROM $TEST_IMAGE_WITH_TAG\nRUN echo plain\n"
        buildFile << buildImageTask('')
        build('buildImage')

        when:
        buildFile << """
            docker {
                buildKit = true
            }
        """
        BuildResult result = build('buildImage')

        then:
        result.task(':buildImage').outcome == TaskOutcome.SUCCESS
        result.output.contains('Created image with ID')
    }

    def "shows the build steps and what they printed"() {
        given:
        new File(projectDir, 'Dockerfile').text = "FROM $TEST_IMAGE_WITH_TAG\nRUN echo MARKER-FROM-RUN\n"
        buildFile << buildImageTask('buildKit = true')

        when:
        BuildResult result = build('buildImage')

        then:
        result.output.contains('RUN echo MARKER-FROM-RUN')
        result.output.contains('MARKER-FROM-RUN')
        result.output.contains('Created image with ID')
    }

    def "shows which step failed and why"() {
        given:
        new File(projectDir, 'Dockerfile').text =
                "FROM $TEST_IMAGE_WITH_TAG\nRUN echo MARKER-BEFORE-FAILURE && exit 7\n"
        buildFile << buildImageTask('buildKit = true')

        when:
        BuildResult result = buildAndFail('buildImage')

        then:
        result.output.contains('MARKER-BEFORE-FAILURE')
        result.output.contains('ERROR:')
        result.output.contains('exit code: 7')
    }

    private String buildImageTask(String extraConfiguration) {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task buildImage(type: DockerBuildImage) {
                inputDir = projectDir
                dockerFile = file('Dockerfile')
                images.add("${createUniqueImageId()}")
                $extraConfiguration
            }

            task removeImage(type: DockerRemoveImage) {
                force = true
                targetImageId buildImage.imageId
            }

            buildImage.finalizedBy tasks.removeImage
        """
    }
}
