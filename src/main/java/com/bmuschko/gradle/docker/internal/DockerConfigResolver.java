package com.bmuschko.gradle.docker.internal;

import java.io.File;

public interface DockerConfigResolver {
    File getDefaultDockerCert();
}
