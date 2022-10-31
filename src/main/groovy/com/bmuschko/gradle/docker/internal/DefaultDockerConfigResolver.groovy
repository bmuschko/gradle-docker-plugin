package com.bmuschko.gradle.docker.internal

import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

import javax.annotation.Nullable

import static com.bmuschko.gradle.docker.internal.OsUtils.isWindows

@CompileStatic
class DefaultDockerConfigResolver {

    private static final Logger logger = Logging.getLogger(DefaultDockerConfigResolver)

    static String getDefaultDockerUrl() {
        getDefaultDockerUrl(
                new File('\\\\.\\pipe\\docker_engine').exists(),
                new File('/var/run/docker.sock').exists(),
                new File("${SystemConfig.getProperty("user.home")}/.docker/run/docker.sock").exists()
        )
    }

    @VisibleForTesting
    protected static String getDefaultDockerUrl(boolean winPipeDockerEngineExists,
                                                boolean varRunDockerSockExists,
                                                boolean userHomeDockerSockExists) {
        String dockerUrl = SystemConfig.getEnv("DOCKER_HOST")
        if (!dockerUrl) {
            boolean isWindows = isWindows()

            if (isWindows) {
                if (winPipeDockerEngineExists) {
                    dockerUrl = 'npipe:////./pipe/docker_engine'
                }
            } else {
                // macOS or Linux
                if (varRunDockerSockExists) {
                    dockerUrl = 'unix:///var/run/docker.sock'
                } else if (userHomeDockerSockExists) {
                    dockerUrl = "unix://${SystemConfig.getProperty('user.home')}/.docker/run/docker.sock"
                }
            }

            if (!dockerUrl) {
                dockerUrl = 'tcp://127.0.0.1:2375'
            }
        }
        logger.info("Default docker.url set to $dockerUrl")
        dockerUrl
    }

    @Nullable
    static File getDefaultDockerCert() {
        String dockerCertPath = SystemConfig.getEnv("DOCKER_CERT_PATH")
        if (dockerCertPath) {
            File certFile = new File(dockerCertPath)
            if (certFile.exists()) {
                return certFile
            }
        }
        return null
    }

}
