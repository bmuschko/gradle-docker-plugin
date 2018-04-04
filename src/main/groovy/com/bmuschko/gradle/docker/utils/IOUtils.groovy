package com.bmuschko.gradle.docker.utils

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
}
