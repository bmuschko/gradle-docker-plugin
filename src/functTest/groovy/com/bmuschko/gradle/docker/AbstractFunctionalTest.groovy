package com.bmuschko.gradle.docker

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

abstract class AbstractFunctionalTest extends Specification {

    public static final String TEST_IMAGE = 'alpine'
    public static final String TEST_IMAGE_TAG = '3.4'
    public static final String TEST_IMAGE_WITH_TAG = "${TEST_IMAGE}:${TEST_IMAGE_TAG}"

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile
    File settingsFile

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile(getBuildFileName())
        settingsFile = temporaryFolder.newFile(getSettingsFileName())
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
    }

    abstract String getBuildFileName()
    abstract String getSettingsFileName()
}
