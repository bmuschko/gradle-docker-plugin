package com.bmuschko.gradle.docker

import com.bmuschko.gradle.docker.utils.CopyDirVisitor
import com.bmuschko.gradle.docker.utils.GradleDsl
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractDocumentationTest extends Specification {

    public static final List<GradleDsl> ALL_DSLS = [GradleDsl.GROOVY, GradleDsl.KOTLIN]

    @TempDir
    File temporaryFolder

    File projectDir

    def setup() {
        projectDir = temporaryFolder
    }

    protected BuildResult build(String... arguments) {
        createAndConfigureGradleRunner(arguments).build()
    }

    private GradleRunner createAndConfigureGradleRunner(String... arguments) {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(arguments + '-s' as List<String>)
            .withPluginClasspath().forwardOutput()
    }

    protected void copySampleCode(String sourcePath) {
        copySampleDirRecursively(new File(getSamplesCodeDir(), sourcePath), projectDir)
    }

    private static File getSamplesDir() {
        new File(System.getProperty('samples.code.dir'))
    }

    private static File getSamplesCodeDir() {
        new File(getSamplesDir(), 'code')
    }

    private static void copySampleDirRecursively(File sourceDir, File targetDir) {
        Path sourcePath = Paths.get(sourceDir.toURI())
        Path targetPath = Paths.get(targetDir.toURI())
        Files.walkFileTree(sourcePath, new CopyDirVisitor(sourcePath, targetPath))
    }
}
