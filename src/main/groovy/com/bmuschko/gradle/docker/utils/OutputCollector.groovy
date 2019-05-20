package com.bmuschko.gradle.docker.utils

import groovy.transform.CompileStatic

import java.util.function.Consumer

/**
 * A utility class that will collect strings until a new line is encountered.
 */
@CompileStatic
final class OutputCollector implements Closeable {

    /**
     * The function that will receive the output once a new line has been encountered.
     */
    private final Consumer<String> output

    /**
     * The buffer to store the strings until a new line has been found.
     */
    private final StringBuffer buffer = new StringBuffer()

    OutputCollector(Consumer<String> output) {
        this.output = output
    }

    /**
     * Accept a string as input. The collector will save the inputs until a new line is encountered, or the collector
     * is closed. The newlines are stripped and not present in the output.
     *
     * @param input The string to accept.
     */
    void accept(String input) {
        // Split the received output segment on line terminators (newlines).
        // We always store the first part in the buffer. All subsequent parts result in
        // a buffer flush.
        // For example, if stream = "output line\n", the string will be split into ["output line", ""].
        // The empty, second part will make sure the line is printed.
        String[] parts = input.split("\\R", -1)
        // We store the first substring in the buffer; all subsequent substrings will flush the buffer.
        buffer.append(parts[0])
        for (int i = 1; i < parts.length; i++) {
            flushBuffer()
            buffer.append(parts[i])
        }
    }

    @Override
    void close() throws IOException {
        if (buffer.length() > 0) {
            flushBuffer()
        }
    }

    /**
     * Flush the current contents of the buffer to the output.
     */
    private void flushBuffer() {
        output.accept(buffer.toString())
        buffer.delete(0, buffer.length())
    }
}
