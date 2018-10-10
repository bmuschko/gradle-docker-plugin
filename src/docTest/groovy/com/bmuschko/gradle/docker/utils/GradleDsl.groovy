package com.bmuschko.gradle.docker.utils

enum GradleDsl {
    GROOVY('groovy', 'gradle'),
    KOTLIN('kotlin', 'gradle.kts')

    private final String language
    private final String fileExtension

    GradleDsl(String language, String fileExtension) {
        this.language = language
        this.fileExtension = fileExtension
    }

    String getLanguage() {
        language
    }

    String getFileExtension() {
        fileExtension
    }
}
