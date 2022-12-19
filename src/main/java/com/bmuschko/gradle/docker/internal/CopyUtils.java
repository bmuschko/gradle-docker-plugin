package com.bmuschko.gradle.docker.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CopyUtils {

    private CopyUtils() { }

    /**
     * Copy regular file inside tempDestination to, or into, hostDestination
     */
    public static void copySingleFile(Path hostDestination, Path tempDestination) throws IOException {

        // ensure regular file does not exist as we don't want clobbering
        if (Files.exists(hostDestination) && !Files.isDirectory(hostDestination)) {
            Files.delete(hostDestination);
        }

        // create parent files of hostPath should they not exist
        if (!Files.exists(hostDestination) && !Files.exists(hostDestination.getParent())) {
            Files.createDirectories(hostDestination.getParent());
        }

        Path parentDirectory = Files.isDirectory(hostDestination) ? hostDestination : hostDestination.getParent();
        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDestination)) {
            files = stream.collect(Collectors.toList());
        }
        Path fileName = Files.isDirectory(hostDestination) ?
                files.get(0).getFileName() : hostDestination.getFileName();

        Path destination = parentDirectory.resolve(fileName);
        Files.move(files.get(0), destination);
    }

    /**
     * Copy files inside tempDestination into hostDestination
     */
    public static void copyMultipleFiles(Path hostDestination, Path tempDestination) throws IOException {

        // Flatten single top-level directory to behave more like docker. Basically
        // we are turning this:
        //
        //     /<requested-host-dir>/base-directory/actual-files-start-here
        //
        // into this:
        //
        //     /<requested-host-dir>/actual-files-start-here
        //
        // gradle does not currently offer any mechanism to do this which
        // is why we have to do the following gymnastics
        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDestination)) {
            files = stream.collect(Collectors.toList());
        }
        if (files.size() == 1) {
            Path dirToFlatten = files.get(0);
            Path dirToFlattenParent = dirToFlatten.getParent();
            Path flatDir = dirToFlattenParent.resolve(UUID.randomUUID().toString());

            // rename origin to escape potential clobbering
            Files.move(dirToFlatten, flatDir);

            // rename files 1 level higher
            List<Path> filesToMove;
            try (Stream<Path> stream = Files.list(flatDir)) {
                filesToMove = stream.collect(Collectors.toList());
            }
            for (Path it : filesToMove) {
                Path movedFile = dirToFlattenParent.resolve(it.getFileName());
                Files.move(it, movedFile);
            }

            try (Stream<Path> walk = Files.walk(flatDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
        }

        // delete regular file should it exist
        if (Files.exists(hostDestination) && !Files.isDirectory(hostDestination)) {
            Files.delete(hostDestination);
        }

        // If directory already exists, rename each file into
        // said directory, otherwise rename entire directory.
        if (Files.exists(hostDestination)) {
            List<Path> tempDestFiles;
            try (Stream<Path> stream = Files.list(tempDestination)) {
                tempDestFiles = stream.collect(Collectors.toList());
            }
            for (Path it : tempDestFiles) {
                Path relativePath = tempDestination.relativize(it);
                Path destFile = hostDestination.resolve(relativePath);
                Files.move(it, destFile);
            }
        } else {
            Files.move(tempDestination, hostDestination);
        }
    }
}
