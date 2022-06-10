package com.bmuschko.gradle.docker.internal.fixtures;

import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TestJarFile {
    private final byte[] buffer = new byte[4096];
    private final File jarSource;
    private final List<ZipEntrySource> entries = new ArrayList<>();

    public TestJarFile(File temporaryFolder) {
        this.jarSource = temporaryFolder;
    }

    public void addClass(String filename, Class<?> classToCopy) throws IOException {
        addClass(filename, classToCopy, null);
    }

    public void addClass(String filename, Class<?> classToCopy, Long time)
        throws IOException {
        File file = getFilePath(filename);
        file.getParentFile().mkdirs();
        InputStream inputStream = getClass().getResourceAsStream(
            "/" + classToCopy.getName().replace('.', '/') + ".class");
        copyToFile(inputStream, file);
        if (time != null) {
            file.setLastModified(time);
        }
        entries.add(new FileSource(filename, file));
    }

    public void addFile(String filename, File fileToCopy) throws IOException {
        File file = getFilePath(filename);
        file.getParentFile().mkdirs();
        try (InputStream inputStream = new FileInputStream(fileToCopy)) {
            copyToFile(inputStream, file);
        }
        this.entries.add(new FileSource(filename, file));
    }

    private File getFilePath(String filename) {
        String[] paths = filename.split("\\/");
        File file = jarSource;
        for (String path : paths) {
            file = new File(file, path);
        }
        return file;
    }

    private void copyToFile(InputStream inputStream, File file) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(file)) {
            copy(inputStream, outputStream);
        }
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }

    public File getJarSource() {
        return jarSource;
    }
}
