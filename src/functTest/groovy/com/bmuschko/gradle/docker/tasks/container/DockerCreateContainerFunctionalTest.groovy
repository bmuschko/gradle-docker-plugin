/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bmuschko.gradle.docker.tasks.container

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class DockerCreateContainerFunctionalTest extends AbstractGroovyDslFunctionalTest {

    def "can setup binds"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                hostConfig.binds = ['/tmp': '/testdata']
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            """
            ${containerRemove()}

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                onNext { info ->
                    if (!info.hostConfig.binds.find { it.path == '/tmp' && it.volume.path == '/testdata' }) {
                        throw new GradleException("Bind not found")
                    }
                }
            }
        """

        expect:
        build('inspectContainer')
    }

    def "can setup and mount a tmpfs"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                hostConfig.tmpFs = ['/testdata': 'rw,noexec,nosuid,size=2m']
                cmd = ['sleep','10']
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            """
            task execContainer(type: DockerExecContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
                withCommand(['grep', 'tmpfs /testdata', '/proc/mounts'])
                successOnExitCodes=[0]
            }

            ${containerRemove()}

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn execContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
                onNext { info ->
                   if (info.hostConfig.tmpFs['/testdata'] != 'rw,noexec,nosuid,size=2m') {
                     throw new GradleException("'testdata' tmpfs not found")
                   }
                }
            }
        """

        expect:
        build('inspectContainer')
    }

    def "can override default MAC address"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['ifconfig']
                macAddress = '02:03:04:05:06:07'
                hostConfig.cpuset = '1'
                labels = ["project.name": project.name]
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            containerLogAndRemove()

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("HWaddr 02:03:04:05:06:07")
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build('logContainer')

        then:
        result.output.contains("HWaddr 02:03:04:05:06:07")
        result.output.contains("Configuration cache entry reused.")
    }

    def "can set multiple environment variables"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['env']

                // add by appending new map to current map
                envVars.set(['one' : 'two', 'three' : 'four'])

                // add by calling helper method N number of times
                withEnvVar('five', 'six')
                withEnvVar('seven', 'eight')
                withEnvVar('nine', 'ten')
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            containerLogAndRemove()

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("one=two")
        result.output.contains("three=four")
        result.output.contains("five=six")
        result.output.contains("seven=eight")
        result.output.contains("nine=ten")
    }

    def "with autoRemove set, the container is removed after stopping"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                hostConfig.autoRemove = true

                // The sleep is to keep the container around to avoid the
                // stopContainer task failing due to the container not existing.
                cmd = ['sleep', '2']
            }
        """

        String containerInspect = """
            task inspectStoppedContainer(type: DockerInspectContainer) {
                dependsOn stopContainer
                targetContainerId startContainer.getContainerId()
                onError { err -> throw err }
                doFirst {
                    // Presumably removing a stopped container doesn't happen immediately
                    Thread.sleep(2000)
                }
            }
        """

        buildFile << containerStart(containerCreationTask)
        buildFile << containerStop()
        buildFile << containerInspect

        when:
        BuildResult result = buildAndFail('inspectStoppedContainer')

        then:
        result.task(':inspectStoppedContainer').outcome == TaskOutcome.FAILED
        result.output.contains('com.github.dockerjava.api.exception.NotFoundException')
    }

    def "without autoRemove set, the container still exists after stopping"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()

                // The sleep is to keep the container around to avoid the
                // stopContainer task failing due to the container not existing.
                cmd = ['sleep', '2']
            }
        """

        String containerInspect = """
            task inspectStoppedContainer(type: DockerInspectContainer) {
                dependsOn stopContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()
            }
        """

        buildFile << containerStart(containerCreationTask)
        buildFile << containerStop()
        buildFile << containerRemove()
        buildFile << containerInspect

        expect:
        build('inspectStoppedContainer')
    }

    def "with publishAll set, all ports get a host binding"() {
        given:
        def exposedPorts = [1234, 2345]
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                hostConfig.autoRemove = true

                cmd = ['sleep', '10']
                exposePorts 'tcp', $exposedPorts
                hostConfig.publishAll = true
            }
        """

        String containerInspect = '''
            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                finalizedBy stopContainer

                targetContainerId startContainer.getContainerId()

                onNext { container ->
                    container.networkSettings.ports.bindings.forEach { exposedPort, bindings ->
                        logger.quiet "$exposedPort.port -> ${bindings.first().hostPortSpec}"
                    }
                }
            }
        '''

        buildFile << containerStart(containerCreationTask)
        buildFile << containerStop()
        buildFile << containerInspect

        when:
        BuildResult buildResult = build('inspectContainer')

        then:
        exposedPorts.every {
            buildResult.output.find(/$it -> \d+/) != null
        }
    }

    def "with health check command set as one string, CMD-SHELL should be used"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()

                healthCheck.cmd('exit 0')
            }
        """

        String containerInspect = '''
            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()

                onNext { container ->
                    if (container.config.healthcheck.test.first() != 'CMD-SHELL') {
                        throw new GradleException("Test does not start with CMD-SHELL: $container.config.healthcheck.test")
                    }
                }
            }
        '''

        buildFile << containerStart(containerCreationTask)
        buildFile << containerRemove()
        buildFile << containerInspect

        expect:
        build('inspectContainer')
    }

    def "with health check command set as array, CMD should be used"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()

                healthCheck.cmd.set(['exit', '0'])
            }
        """

        String containerInspect = '''
            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()

                onNext { container ->
                    if (container.config.healthcheck.test.first() != 'CMD') {
                        throw new GradleException("Test does not start with CMD: $container.config.healthcheck.test")
                    }
                }
            }
        '''

        buildFile << containerStart(containerCreationTask)
        buildFile << containerRemove()
        buildFile << containerInspect

        expect:
        build('inspectContainer')
    }

    def "Can build an image, create a container and add/drop capabilities"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()

                hostConfig.capAdd = ['NET_ADMIN']
                hostConfig.capDrop = ['CHOWN']
            }
        """

        String containerInspect = '''
            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                finalizedBy removeContainer
                targetContainerId startContainer.getContainerId()

                onNext { c ->
                    if(c.hostConfig.capAdd.size() != 1) {
                        throw new GradleException("Invalid capAdd value!")
                    }
                    if(c.hostConfig.capAdd.first().toString() != 'NET_ADMIN') {
                        throw new GradleException("Invalid capAdd value!")
                    }
                    if(c.hostConfig.capDrop.size() != 1) {
                        throw new GradleException("Invalid capDrop value!")
                    }
                    if(c.hostConfig.capDrop.first().toString() != 'CHOWN') {
                        throw new GradleException("Invalid capDrop value!")
                    }
                }
            }
        '''

        buildFile << containerStart(containerCreationTask)
        buildFile << containerRemove()
        buildFile << containerInspect

        expect:
        build('inspectContainer')
    }

    def "can run with configuration cache"() {
        given:
        String containerCreationTask = """
            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage
                targetImageId pullImage.getImage()
                cmd = ['echo', 'Hello, world!']
            }
        """
        buildFile <<
            containerStart(containerCreationTask) <<
            containerLogAndRemove()

        when:
        BuildResult result = build('logContainer')

        then:
        result.output.contains("Hello, world!")
        result.output.contains("0 problems were found storing the configuration cache.")

        when:
        result = build('logContainer')

        then:
        result.output.contains("Configuration cache entry reused.")
    }

    static String containerStart(String containerCreationTask) {
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerExecContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task pullImage(type: DockerPullImage) {
                image = '$TEST_IMAGE:$TEST_IMAGE_TAG'
            }

            ${containerCreationTask}

            task startContainer(type: DockerStartContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
            }
        """
    }

    static String containerLogAndRemove() {
        """
            ${containerRemove()}

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
            }

            task logContainer(type: DockerLogsContainer) {
                dependsOn inspectContainer
                finalizedBy removeContainer
                targetContainerId inspectContainer.getContainerId()
                follow = true
                tailAll = true
            }
        """
    }

    static String containerStop() {
        """
            task stopContainer(type: DockerStopContainer) {
                dependsOn startContainer
                targetContainerId startContainer.getContainerId()
            }
        """
    }

    static String containerRemove() {
        """
            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId startContainer.getContainerId()
            }
        """
    }
}
