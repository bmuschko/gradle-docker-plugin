package com.bmuschko.gradle.docker.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultDockerConfigResolver implements DockerConfigResolver {

    private static final Logger logger = Logging.getLogger(DefaultDockerConfigResolver.class);

    @Override
    public String getDefaultDockerUrl() {
        String dockerUrl = getEnv("DOCKER_HOST");
        if (dockerUrl == null) {
            if (OsUtils.isWindows()) {
                if (isFileExists("\\\\.\\pipe\\docker_engine")) {
                    dockerUrl = "npipe:////./pipe/docker_engine";
                }
            } else {
                // macOS or Linux
                if (isFileExists("/var/run/docker.sock")) {
                    dockerUrl = "unix:///var/run/docker.sock";
                } else if (isFileExists(System.getProperty("user.home") + "/.docker/run/docker.sock")) {
                    dockerUrl = "unix://" + System.getProperty("user.home") + "/.docker/run/docker.sock";
                }
            }


            if (dockerUrl == null) {
                dockerUrl = "tcp://127.0.0.1:2375";
            }
        }

        logger.info("Default docker.url set to " + dockerUrl);
        return dockerUrl;
    }

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
