package com.bmuschko.gradle.docker.internal

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import java.util.stream.Stream

class CopyUtilsTest extends Specification {

    @TempDir
    Path workingDir

    @TempDir
    Path tempDir

    def "test copyMultipleFiles with multiple files"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createDirectories(hostDir)
        tempDir.resolve("file1.txt").text = ""
        tempDir.resolve("file2.txt").text = ""

        when:
        CopyUtils.copyMultipleFiles(hostDir, tempDir)

        then:
        List<Path> copiedFiles = listFiles(hostDir)
        copiedFiles.size() == 2
        copiedFiles.contains(Path.of("file1.txt"))
        copiedFiles.contains(Path.of("file2.txt"))
        listFiles(tempDir).isEmpty()
    }

    def "test copyMultipleFiles with multiple files and non-existent hostDir"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        tempDir.resolve("file1.txt").text = ""
        tempDir.resolve("file2.txt").text = ""

        when:
        CopyUtils.copyMultipleFiles(hostDir, tempDir)

        then:
        List<Path> copiedFiles = listFiles(hostDir)
        copiedFiles.size() == 2
        copiedFiles.contains(Path.of("file1.txt"))
        copiedFiles.contains(Path.of("file2.txt"))
        listFiles(tempDir).isEmpty()
    }

    def "test copyMultipleFiles with nested files"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createDirectories(hostDir)
        tempDir.resolve("file1.txt").text = ""
        def nestedTempDir = tempDir.resolve("nested");
        Files.createDirectories(nestedTempDir)
        nestedTempDir.resolve("file2.txt").text = ""

        when:
        CopyUtils.copyMultipleFiles(hostDir, tempDir)

        then:
        List<Path> copiedFiles = listFiles(hostDir)
        copiedFiles.size() == 2
        copiedFiles.contains(Path.of("file1.txt"))
        copiedFiles.contains(Path.of("nested/file2.txt"))
        listFiles(tempDir).isEmpty()
    }

    def "test copyMultipleFiles with nested dir"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createDirectories(hostDir)
        def nestedTempDir = tempDir.resolve("nested");
        Files.createDirectories(nestedTempDir)
        nestedTempDir.resolve("file1.txt").text = ""
        nestedTempDir.resolve("file2.txt").text = ""

        when:
        CopyUtils.copyMultipleFiles(hostDir, tempDir)

        then:
        List<Path> copiedFiles = listFiles(hostDir)
        copiedFiles.size() == 2
        copiedFiles.contains(Path.of("file1.txt"))
        copiedFiles.contains(Path.of("file2.txt"))
        listFiles(tempDir).isEmpty()
    }

    def "test copyMultipleFiles with bad hostDir"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createFile(hostDir)
        def nestedTempDir = tempDir.resolve("nested");
        Files.createDirectories(nestedTempDir)
        nestedTempDir.resolve("file1.txt").text = ""
        nestedTempDir.resolve("file2.txt").text = ""

        when:
        CopyUtils.copyMultipleFiles(hostDir, tempDir)

        then:
        List<Path> copiedFiles = listFiles(hostDir)
        copiedFiles.size() == 2
        copiedFiles.contains(Path.of("file1.txt"))
        copiedFiles.contains(Path.of("file2.txt"))
        listFiles(tempDir).isEmpty()
    }

    def "test copySingleFile with single file"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createDirectories(hostDir);
        tempDir.resolve("file1.txt").text = ""

        when:
        CopyUtils.copySingleFile(hostDir, tempDir)

        then:
        [Path.of("file1.txt")] == listFiles(hostDir)
        [] == listFiles(tempDir)
    }

    def "test copySingleFile with single file and existing hostDir file"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        Files.createDirectories(hostDir)
        def hostFile = hostDir.resolve("file1.txt")
        Files.createFile(hostFile)
        tempDir.resolve("file1.txt").text = ""

        when:
        CopyUtils.copySingleFile(hostFile, tempDir)

        then:
        [Path.of("file1.txt")] == listFiles(hostDir)
        [] == listFiles(tempDir)
    }

    def "test copySingleFile with single file and non-existent hostFile"() {
        given:
        def hostDir = workingDir.resolve("hostDir")
        //Files.createDirectories(hostDir)
        def hostFile = hostDir.resolve("file1.txt")
        //Files.createFile(hostFile)
        tempDir.resolve("file1.txt").text = ""

        when:
        CopyUtils.copySingleFile(hostFile, tempDir)

        then:
        [Path.of("file1.txt")] == listFiles(hostDir)
        [] == listFiles(tempDir)
    }

    static List<Path> listFiles(final Path path) {
        if (!Files.exists(path)) {
            return []
        }
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter { !Files.isDirectory(it) }
                .map { path.relativize(it) }
                .collect(Collectors.toList())
        }
    }
}
