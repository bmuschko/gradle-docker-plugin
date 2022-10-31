package com.bmuschko.gradle.docker.internal

import groovy.transform.CompileStatic

/**
 * The internal abstraction on top of JVM class {@link System}. The main purpose is to provide a way to manipulate
 * these properties and environments in the unit test environment.
 */
@CompileStatic
class SystemConfig {

    private static Map<String, String> OVERRIDE_ENVS = [:]

    static String getProperty(String key) {
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

    // visible for testing
    static void setProperty(String key, String value) {
        System.setProperty(key, value)
    }

    // visible for testing
    static void setEnv(String name, String value) {
        OVERRIDE_ENVS.put(name, value)
    }

    // visible for testing
    static void clear() {
        OVERRIDE_ENVS.clear()
    }

}
