package com.bmuschko.gradle.docker.internal;

public final class OsUtils {
    private OsUtils() {
    }

    public static Boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
