/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bmuschko.gradle.docker.domain

import groovy.transform.CompileStatic
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input

/**
 * Class holding metadata for an arbitrary exec livenessProbe.
 */
@CompileStatic
class ExecProbe {

    /**
     * Indicates how long we poll until match is found.
     */
    @Input
    long pollTime

    /**
     * Indicates how long we wait until next poll.
     */
    @Input
    long pollInterval

    ExecProbe(long pollTime, long pollInterval) {
        if (pollInterval > pollTime) {
            throw new GradleException("pollInterval must be greater than pollTime: pollInterval=${pollInterval}, pollTime=${pollTime}")
        }

        this.pollTime = pollTime
        this.pollInterval = pollInterval
    }

    @Override
    String toString() {
        "pollTime=${pollTime}, pollInterval=${pollInterval}"
    }
}
