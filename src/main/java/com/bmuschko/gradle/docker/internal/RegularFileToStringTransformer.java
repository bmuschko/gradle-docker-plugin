package com.bmuschko.gradle.docker.internal;

import org.gradle.api.Transformer;
import org.gradle.api.file.RegularFile;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public class RegularFileToStringTransformer implements Transformer<String, RegularFile>, Serializable {
    @Override
    public String transform(RegularFile it) {
        File file = it.getAsFile();
        if (file.exists()) {
            try {
                return Files.readString(file.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }
}
