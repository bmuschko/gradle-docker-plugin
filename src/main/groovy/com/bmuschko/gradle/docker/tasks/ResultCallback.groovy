package com.bmuschko.gradle.docker.tasks

import org.gradle.api.tasks.Internal

trait ResultCallback {
    /**
     * Reacts to data returned by an operation.
     */
    @Internal
    Closure onNext
}
