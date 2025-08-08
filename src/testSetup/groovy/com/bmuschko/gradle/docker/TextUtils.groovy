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

    /**
     * Compares two strings while ignoring differences in line endings.
     * Treats \r\n (Windows), \n (Unix), and \r (old Mac) as equivalent.
     *
     * @param str1 First string to compare
     * @param str2 Second string to compare
     * @return true if strings are equal (ignoring line ending differences), false otherwise
     */
    static boolean equalsIgnoreLineEndings(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true
        }
        if (str1 == null || str2 == null) {
            return false
        }

        // Normalize line endings by replacing all variations with \n
        String normalized1 = normalizeLineEndings(str1)
        String normalized2 = normalizeLineEndings(str2)
        return normalized1.equals(normalized2)
    }

    /**
     * Normalizes line endings in a string by converting all line ending variations to \n.
     *
     * @param str String to normalize
     * @return String with normalized line endings
     */
    private static String normalizeLineEndings(String str) {
        // Replace Windows line endings first, then Mac line endings
        return str.replace("\r\n", "\n").replace("\r", "\n")
    }
}
