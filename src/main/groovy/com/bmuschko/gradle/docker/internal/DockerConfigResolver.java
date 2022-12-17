package com.bmuschko.gradle.docker.internal;

import java.io.File;

public interface DockerConfigResolver {
    public abstract String getDefaultDockerUrl();

    public abstract File getDefaultDockerCert();
}
