package com.bmuschko.gradle.docker.tasks.image.data

import javax.annotation.Nullable

/**
 * Input data for a from instruction.
 *
 * @since 4.0
 */
class From {
    final String image
    final @Nullable String stageName

    From(String image) {
        this.image = image
    }

    From(String image, @Nullable String stageName) {
        this.image = image
        this.stageName = stageName
    }
}
