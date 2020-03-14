package com.bmuschko.gradle.docker.tasks.network

import com.bmuschko.gradle.docker.AbstractGroovyDslFunctionalTest
import org.gradle.testkit.runner.BuildResult

class DockerNetworkFunctionalTest extends AbstractGroovyDslFunctionalTest {
    /**
     * Creation and removal are tested together because leftover networks can be extremely annoying.
     */
    def 'Can create and tear down a network'() {
        given:
        final uniqueNetworkName = createUniqueNetworkName()
        buildFile << """
            import com.bmuschko.gradle.docker.tasks.network.DockerCreateNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerRemoveNetwork
            import com.bmuschko.gradle.docker.tasks.network.DockerInspectNetwork

            task createNetwork(type: DockerCreateNetwork) {
                networkName = '$uniqueNetworkName'
            }

            task removeNetwork(type: DockerRemoveNetwork) {
                targetNetworkId createNetwork.getNetworkId()
            }

            task inspectNetwork(type: DockerInspectNetwork) {
                targetNetworkId createNetwork.getNetworkId()
                dependsOn createNetwork

                onNext { network ->
                    println 'inspectNetwork ' + network.name
                }
            }

            task inspectNoNetwork(type: DockerInspectNetwork) {
                targetNetworkId createNetwork.getNetworkId()
                dependsOn removeNetwork

                onError { error ->
                    println 'inspectNoNetwork ' + error
                }
            }

            removeNetwork.mustRunAfter inspectNetwork
            createNetwork.finalizedBy removeNetwork
        """

        when:
        BuildResult result = build('inspectNetwork', 'inspectNoNetwork')

        then:
        result.output.contains("inspectNetwork $uniqueNetworkName")
        result.output.find(/inspectNoNetwork.*network [a-z0-9]+ not found/)
    }
}
