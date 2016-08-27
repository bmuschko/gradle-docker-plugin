package com.bmuschko.gradle.docker.tasks.image

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.TestPrecondition
import org.gradle.testkit.runner.BuildResult
import spock.lang.Requires

import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires({ TestPrecondition.DOCKER_SERVER_INFO_URL_REACHABLE })
class DockerRemoveImageFunctionalTest extends AbstractFunctionalTest {

    def "can remove image"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage

            task dockerfile(type: Dockerfile) {
                from 'alpine:3.4'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }
            
            task removeImage(type: DockerRemoveImage) {
                dependsOn buildImage
                force = true
                targetImageId { buildImage.getImageId() }
            }
            
            task removeImageAndCheckRemoval(type: DockerListImages) {
				dependsOn removeImage
    			showAll = true
    			filters = '{"dangling":["true"]}'
			}
        """

        when:
        BuildResult result = build('removeImageAndCheckRemoval')

        then:
        !result.output.contains("repository")
    }

    def "can remove image tagged in multiple repositories"() {
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.image.Dockerfile
            import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
            import com.bmuschko.gradle.docker.tasks.image.DockerListImages
            import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
            import com.bmuschko.gradle.docker.tasks.image.DockerTagImage

            task dockerfile(type: Dockerfile) {
                from 'alpine:3.4'
            }

            task buildImage(type: DockerBuildImage) {
                dependsOn dockerfile
                inputDir = file("build/docker")
            }
            
            task tagImage(type: DockerTagImage) {
                dependsOn buildImage
                repository = "repository"
                tag = "tag2"
                targetImageId { buildImage.getImageId() }
            }
            
            task tagImageSecondTime(type: DockerTagImage) {
                dependsOn tagImage
                repository = "repository"
                tag = "tag2"
                targetImageId { buildImage.getImageId() }
            }
            
            task removeImage(type: DockerRemoveImage) {
                dependsOn tagImageSecondTime
                force = true
                targetImageId { buildImage.getImageId() }
            }

			task removeImageAndCheckRemoval(type: DockerListImages) {
				dependsOn removeImage
    			showAll = true
    			filters = '{"dangling":["true"]}'
			}
            
        """

        when:
        BuildResult result = build('removeImageAndCheckRemoval')

        then:
        !result.output.contains("repository")
    }
}
