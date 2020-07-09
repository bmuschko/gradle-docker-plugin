package com.bmuschko.gradle.docker

final class TextUtils {
    private TextUtils() {}

    /**
     * Escape backslashes in windows in Docker files.
     */
    static String escapeFilePath(File file) {
        if (file.separatorChar == '\\') {
            return file.toString().replace('\\', '\\\\')
        }

        file.toString()
    }

    static Boolean equalsIgnoreLineEndings(String a, String b) {
        a.replace("\r", "") == b.replace("\r", "")
    }
    
    static Boolean containsIgnoreLineEndings(String a, String b) {
        a.replace("\r", "").contains(b.replace("\r", ""))
    }
}
