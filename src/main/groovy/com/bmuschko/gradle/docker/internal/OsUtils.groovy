package com.bmuschko.gradle.docker.internal

final class OsUtils {
    private OsUtils() {}

    static Boolean isWindows() {
        System.getProperty("os.name").toLowerCase().indexOf("win") >= 0
    }
}
