package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerNetworkFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE = 'alpine:3.4'
    private static final String TEST_SUBNET = '10.11.12.0/30'

    def "can create and tear down a network"() {
        given:
        String uniqueNetworkName = createUniqueNetworkName()
        buildFile << networkUsage(uniqueNetworkName)
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork

            task inspectNoNetwork(type: DockerInspectNetwork) {
                targetNetworkId createNetwork.getNetworkId()
                dependsOn removeNetwork

                onError { error ->
                    println 'inspectNoNetwork ' + error
                }
            }

            inspectNetwork.finalizedBy removeNetwork
        """

        when:
        BuildResult result = build('inspectNetwork', 'inspectNoNetwork')

        then:
        result.output.contains("inspectNetwork $uniqueNetworkName")
        result.output.find(/inspectNoNetwork.*network [a-z0-9]+ not found/)
    }

    def "can create and tear down a network with a specified subnet"() {
        given:
        String uniqueNetworkName = createUniqueNetworkName()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork

            task createNetworkWithSubnet(type: DockerCreateNetwork) {
                networkName = '$uniqueNetworkName'
                ipam.config = [
                    [subnet : '$TEST_SUBNET']
                ]
            }

            task removeNetworkWithSubnet(type: DockerRemoveNetwork) {
                targetNetworkId createNetworkWithSubnet.getNetworkId()
            }

            task inspectNetworkWithSubnet(type: DockerInspectNetwork) {
                dependsOn createNetworkWithSubnet
                targetNetworkId createNetworkWithSubnet.getNetworkId()

                onNext { network ->
                    println 'inspectNetworkWithSubnet subnet: ' + network.getIpam().getConfig().get(0).getSubnet()
                }
            }

            inspectNetworkWithSubnet.finalizedBy removeNetworkWithSubnet
        """

        when:
        BuildResult result = build('inspectNetworkWithSubnet')

        then:
        result.output.contains("inspectNetworkWithSubnet subnet: $TEST_SUBNET")
    }

    def "can create a container and assign a network and alias"() {
        given:
        String uniqueNetworkName = createUniqueNetworkName()
        buildFile << networkUsage(uniqueNetworkName)
        buildFile << pullImageTask()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
            import com.bmuschko.gradle.docker.tasks.container.DockerInspectContainer

            task createContainer(type: DockerCreateContainer) {
                dependsOn pullImage, inspectNetwork
                targetImageId pullImage.getImage()
                hostConfig.network = createNetwork.getNetworkId()
                networkAliases = ['some-alias']
                cmd = ['/bin/sh']
            }

            task inspectContainer(type: DockerInspectContainer) {
                dependsOn createContainer
                targetContainerId createContainer.getContainerId()
                onNext { container ->
                    println container.networkSettings.networks['$uniqueNetworkName'].aliases
                }
            }

            ${containerRemoveTask()}

            inspectContainer.finalizedBy removeContainer, removeNetwork
        """

        when:
        BuildResult result = build('inspectContainer')

        then:
        result.output.contains('[some-alias]')
    }

    static String pullImageTask() {
        """
            import com.bmuschko.gradle.docker.tasks.image.DockerPullImage

            task pullImage(type: DockerPullImage) {
                image = '${IMAGE}'
            }
        """
    }

    static String containerRemoveTask() {
        """
            import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer

            task removeContainer(type: DockerRemoveContainer) {
                removeVolumes = true
                force = true
                targetContainerId createContainer.getContainerId()
            }
        """
    }

    static String networkUsage(String networkName) {
        """
            import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork

            task createNetwork(type: DockerCreateNetwork) {
                networkName = '$networkName'
            }

            task removeNetwork(type: DockerRemoveNetwork) {
                targetNetworkId createNetwork.getNetworkId()
            }

            task inspectNetwork(type: DockerInspectNetwork) {
                dependsOn createNetwork
                targetNetworkId createNetwork.getNetworkId()

                onNext { network ->
                    println 'inspectNetwork ' + network.name
                }
            }
        """
    }
}
