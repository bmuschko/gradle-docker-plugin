package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static com.bmuschko.gradle.docker.internal.OsUtils.isWindows

abstract class AbstractFunctionalTest extends Specification {

    public static final String TEST_IMAGE = 'alpine'
    public static final String TEST_IMAGE_TAG = '3.4'
    public static final String TEST_IMAGE_WITH_TAG = "${TEST_IMAGE}:${TEST_IMAGE_TAG}"
    public static final String CONFIGURATION_CACHE = '--configuration-cache'

    @TempDir
    File temporaryFolder

    File projectDir
    File buildFile
    File settingsFile
    Map<String, String> envVars = new HashMap<>()

    def setup() {
        projectDir = temporaryFolder
        buildFile = new File(temporaryFolder, getBuildFileName())
        buildFile.createNewFile()
        settingsFile = new File(temporaryFolder, getSettingsFileName())
        settingsFile.createNewFile()

        if (isWindows()) {
            envVars.put("PATH", System.getenv("PATH"))
        }
    }

    /**
     * Adds an environment variable for gradle runner
     * @param variable variable name
     * @param value variable value
     */
    protected void addEnvVar(String variable, String value) {
        envVars.put(variable, value)
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).build()
    }

    protected BuildResult buildAndFail(String... arguments) {
        createAndConfigureGradleRunner(arguments).buildAndFail()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(arguments + '-s' as List<String>)
            .withPluginClasspath()
            .withEnvironment(envVars)
    }

    protected File file(String relativePath) {
        File file = new File(projectDir, relativePath)
        assert file.exists()
        file
    }

    abstract String getBuildFileName()
    abstract String getSettingsFileName()
}
