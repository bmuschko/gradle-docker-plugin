package com.bmuschko.gradle.docker.internal;

import java.io.File;

public interface DockerConfigResolver {
    String getDefaultDockerUrl();

    File getDefaultDockerCert();
}
