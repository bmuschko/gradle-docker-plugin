package com.bmuschko.gradle.docker.utils

import org.gradle.api.Project
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.service.ServiceRegistry

final class IOUtils {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4

    private IOUtils() { }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE]
        int n
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n)
        }
    }

    static void closeQuietly(Closeable toClose) {
        try {
            if (toClose != null) {
                toClose.close()
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    /**
     * Create a progress logger for an arbitrary project and class.
     *
     * @param project the project to create a ProgressLogger for.
     * @param clazz optional class to pair the ProgressLogger to. Defaults to _this_ class if null.
     * @return instance of ProgressLogger.
     */
    static ProgressLogger getProgressLogger(final Project project, final Class clazz) {
        ServiceRegistry registry = project.gradle.getServices()
        ProgressLoggerFactory factory = registry.get(ProgressLoggerFactory)
        ProgressLogger progressLogger = factory.newOperation(Objects.requireNonNull(clazz))
        progressLogger.setDescription("ProgressLogger for ${clazz.getSimpleName()}")
        progressLogger.setLoggingHeader(null)
    }
}
