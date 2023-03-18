package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.AbstractFunctionalTest
import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerNetworkFunctionalTest extends AbstractGroovyDslFunctionalTest {

    private static final String IMAGE = AbstractFunctionalTest.TEST_IMAGE_WITH_TAG
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

    def "can create and tear down a network with a specified subnet"() {
        given:
        String uniqueNetworkName = createUniqueNetworkName()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork
            import static com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork.Ipam.Config

            task createNetworkWithSubnet(type: DockerCreateNetwork) {
                networkName = '$uniqueNetworkName'
                Config config = new Config(subnet: '$TEST_SUBNET')
                ipam.configs.add(config)
            }

            task removeNetworkWithSubnet(type: DockerRemoveNetwork) {
                targetNetworkId createNetworkWithSubnet.getNetworkId()
            }

            task inspectNetworkWithSubnet(type: DockerInspectNetwork) {
                dependsOn createNetworkWithSubnet
                targetNetworkId createNetworkWithSubnet.getNetworkId()
                
                onNext { network ->
                    println 'inspectNetworkWithSubnet subnet: ' + network.ipam.config.get(0).subnet
                }
            }

            inspectNetworkWithSubnet.finalizedBy removeNetworkWithSubnet
        """

        when:
        BuildResult result = build('inspectNetworkWithSubnet')

        then:
        result.output.contains("inspectNetworkWithSubnet subnet: $TEST_SUBNET")
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
