package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static com.bmuschko.gradle.docker.internal.OsUtils.isWindows

abstract class AbstractFunctionalTest extends Specification {

    public static final String TEST_IMAGE = 'alpine'
    public static final String TEST_IMAGE_TAG = '3.17.2'
    public static final String TEST_IMAGE_WITH_TAG = "${TEST_IMAGE}:${TEST_IMAGE_TAG}"

    @TempDir
    File temporaryFolder

    File projectDir
    File buildFile
    File settingsFile
    Map<String, String> envVars = new HashMap<>()
    String version

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

    /**
     * Set a specific Gradle version for gradle runner
     * @param version version string
     */
    protected void useGradleVersion(String version) {
        this.version = version
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).build()
    }

    protected BuildResult buildAndFail(String... arguments) {
        createAndConfigureGradleRunner(arguments).buildAndFail()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        def gradleRunner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(arguments + '-s' + '--configuration-cache' as List<String>)
            .withPluginClasspath()
            .withEnvironment(envVars)
        if (version) {
            gradleRunner.withGradleVersion(version)
        }
        return gradleRunner
    }

    protected File file(String relativePath) {
        File file = new File(projectDir, relativePath)
        assert file.exists()
        file
    }

    abstract String getBuildFileName()
    abstract String getSettingsFileName()
}
