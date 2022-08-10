buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.bmuschko:gradle-docker-plugin:{gradle-project-version}")
    }
}

apply(plugin = "com.bmuschko.docker-remote-api")