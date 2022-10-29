package com.bmuschko.gradle.docker.internal

final class OsUtils {
    private OsUtils() {}

    static Boolean isWindows() {
        SystemConfig.getProperty("os.name").toLowerCase().indexOf("win") >= 0
    }
}
