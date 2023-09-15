package com.bmuschko.gradle.docker.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

import javax.annotation.Nullable;
import java.io.File;

public abstract class DefaultDockerUrlValueSource implements ValueSource<String, ValueSourceParameters.None> {

    private static final Logger logger = Logging.getLogger(DefaultDockerUrlValueSource.class);

    @Override
    public String obtain() {
        String dockerUrl = getEnv("DOCKER_HOST");
        if (dockerUrl == null) {
            if (OsUtils.isWindows()) {
                if (isFileExists("\\\\.\\pipe\\docker_engine")) {
                    dockerUrl = "npipe:////./pipe/docker_engine";
                }
            } else {
                //Added for possibility to run integration tests, also makes it possible to override use of /var/run/
                boolean skipVarRun = Boolean.valueOf(System.getProperty("com.bmuschko.gradle.docker.internal.DefaultDockerUrlValueSource.skipCheckOfVarRun", "false"));
                // macOS or Linux
                if (isFileExists("/var/run/docker.sock") && !skipVarRun) {
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
    String getEnv(String name) {
        return System.getenv(name);
    }

    boolean isFileExists(String path) {
        return new File(path).exists();
    }
}
