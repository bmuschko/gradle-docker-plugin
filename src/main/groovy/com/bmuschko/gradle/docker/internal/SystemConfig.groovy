package com.bmuschko.gradle.docker.internal

import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic

/**
 * The internal abstraction on top of JVM class {@link System}. The main purpose is to provide a way to manipulate
 * these properties and environments in the unit test environment.
 */
@CompileStatic
class SystemConfig {

    private static Map<String, String> OVERRIDE_PROPERTIES = [:]
    private static Map<String, String> OVERRIDE_ENVS = [:]

    static String getProperty(String key) {
        if (OVERRIDE_PROPERTIES.containsKey(key)) {
            return OVERRIDE_PROPERTIES.get(key)
        }
        return System.getProperty(key)
    }

    static String getEnv(String name) {
        if (OVERRIDE_ENVS.containsKey(name)) {
            return OVERRIDE_ENVS.get(name)
        }
        return System.getenv(name)
    }

    static String getEnvOrDefault(String name, String defaultValue) {
        return getEnv(name) ?: defaultValue
    }

    @VisibleForTesting
    static void setProperty(String key, String value) {
        OVERRIDE_PROPERTIES.put(key, value)
    }

    @VisibleForTesting
    static void setEnv(String name, String value) {
        OVERRIDE_ENVS.put(name, value)
    }

    @VisibleForTesting
    static void clear() {
        OVERRIDE_PROPERTIES.clear()
        OVERRIDE_ENVS.clear()
    }

}
