package com.bmuschko.gradle.docker

class TextUtils {
    /**
     * Escape backslashes in windows in docker files
     */
    static String escapeFilePath(File file) {
        if (file.separatorChar == '\\') {
            file.toString().replace('\\', '\\\\')
        }
        else {
            file.toString()
        }
    }
    static Boolean equalsIgnoreLineEndings(String a, String b) {
        a.replace("\r", "") == b.replace("\r", "")
    }
    
    static Boolean containsIgnoreLineEndings(String a, String b) {
        a.replace("\r", "").contains(b.replace("\r", ""))
    }
}
