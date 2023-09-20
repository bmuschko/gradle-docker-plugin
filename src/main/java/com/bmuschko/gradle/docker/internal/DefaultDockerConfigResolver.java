package com.bmuschko.gradle.docker.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultDockerConfigResolver implements DockerConfigResolver {
    @Nullable
    @Override
    public File getDefaultDockerCert() {
        String dockerCertPath = getEnv("DOCKER_CERT_PATH");
        if (dockerCertPath != null) {
            File certFile = new File(dockerCertPath);
            if (certFile.exists()) {
                return certFile;
            }
        }
        return null;
    }

    @Nullable
    String getEnv(String name) {
        return System.getenv(name);
    }

    boolean isFileExists(String path) {
        return new File(path).exists();
    }
}
