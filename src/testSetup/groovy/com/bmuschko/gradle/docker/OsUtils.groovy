package com.bmuschko.gradle.docker

final class OsUtils {
    private OsUtils() {}

    static Boolean isWindows() {
        System.getProperty("os.name").toLowerCase().indexOf("win") >= 0
    }
}
