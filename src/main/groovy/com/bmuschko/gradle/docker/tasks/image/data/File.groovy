package com.bmuschko.gradle.docker.tasks.image.data

import javax.annotation.Nullable

/**
 * Input data for a copy or add instruction.
 *
 * @since 4.0
 */
class File {
    final String src
    final String dest
    final @Nullable String flags

    File(String src, String dest) {
        this.src = src
        this.dest = dest
    }

    File(String src, String dest, @Nullable String flags) {
        this.src = src
        this.dest = dest
        this.flags = flags
    }
}
