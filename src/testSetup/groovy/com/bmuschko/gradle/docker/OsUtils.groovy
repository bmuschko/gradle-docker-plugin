package com.bmuschko.gradle.docker

class OsUtils {
    static Boolean isWindows() {
        System.getProperty("os.name").toLowerCase().indexOf("win") >= 0
    }
}
