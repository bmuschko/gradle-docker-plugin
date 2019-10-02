package com.bmuschko.gradle.docker.tasks

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.github.dockerjava.api.model.AuthConfig
import groovy.transform.CompileStatic

@CompileStatic
abstract class AbstractCredentialsAwareRemoteApiTask extends AbstractDockerRemoteApiTask
    implements RegistryCredentialsAware {

    /**
     * {@inheritDoc}
     */
    DockerRegistryCredentials registryCredentials

    protected AuthConfig resolveAuthConfig(String image) {
        if(registryCredentials && registryCredentials.isValid()) {
            AuthConfig authConfig = new AuthConfig()
                .withRegistryAddress(registryCredentials.url.get())

            if (registryCredentials.username.isPresent()) {
                authConfig.withUsername(registryCredentials.username.get())
            }

            if (registryCredentials.password.isPresent()) {
                authConfig.withPassword(registryCredentials.password.get())
            }

            if (registryCredentials.email.isPresent()) {
                authConfig.withEmail(registryCredentials.email.get())
            }
            return authConfig
        }
        return getAuthConfig(image)
    }
}
