package com.bmuschko.gradle.docker.utils

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class CopyDirVisitor extends SimpleFileVisitor<Path> {
    private final Path sourceDir
    private final Path targetDir

    CopyDirVisitor(Path sourceDir, Path targetDir) {
        this.sourceDir = sourceDir
        this.targetDir = targetDir
    }

    @Override
    FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetPath = targetDir.resolve(sourceDir.relativize(dir))

        if(!Files.exists(targetPath)){
            Files.createDirectory(targetPath)
        }

        return FileVisitResult.CONTINUE
    }

    @Override
    FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.copy(file, targetDir.resolve(sourceDir.relativize(file)), StandardCopyOption.REPLACE_EXISTING)
        return FileVisitResult.CONTINUE
    }
}
