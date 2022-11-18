package com.bmuschko.gradle.docker.internal;

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

public final class IOUtils {

    private IOUtils() { }

    public static void closeQuietly(Closeable toClose) {
        try {
            if (toClose != null) {
                toClose.close();
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    /**
     * Create a progress logger for an arbitrary project and class.
     *
     * @param registry the service registry.
     * @param clazz    optional class to pair the ProgressLogger to. Defaults to _this_ class if null.
     * @return instance of ProgressLogger.
     */
    public static <T> ProgressLogger getProgressLogger(final ServiceRegistry registry, final Class<T> clazz) {
        ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory.class);
        ProgressLogger progressLogger = factory.newOperation(Objects.requireNonNull(clazz));
        return progressLogger.setDescription("ProgressLogger for " + clazz.getSimpleName());
    }
}
