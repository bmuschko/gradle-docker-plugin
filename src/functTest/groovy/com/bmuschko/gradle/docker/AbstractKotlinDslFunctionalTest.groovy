package com.bmuschko.gradle.docker

abstract class AbstractKotlinDslFunctionalTest extends AbstractFunctionalTest {

    @Override
    String getBuildFileName() {
        'build.gradle.kts'
    }

    @Override
    String getSettingsFileName() {
        'settings.gradle.kts'
    }
}
