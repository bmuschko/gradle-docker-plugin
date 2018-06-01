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

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input

/**
 *  Class holding metadata for an arbitrary probe.
 */
class LivenessProbe {

    @Input
    long pollTime // how long we poll until match is found

    @Input
    long pollInterval // how long we wait until next poll

    @Input
    String logContains // halt polling on logs containing this String

    LivenessProbe(long pollTime, long pollInterval, String logContains) {
        if (pollInterval > pollTime) {
            throw new GradleException("pollInterval must be greater than pollTime: pollInterval=${pollInterval}, pollTime=${pollTime}")
        }

        String localLogContains = Objects.requireNonNull(logContains).trim()
        if (localLogContains) {
            this.pollTime = pollTime
            this.pollInterval = pollInterval
            this.logContains = localLogContains
        } else {
            throw new GradleException("logContains must be a valid non-empty String")
        }
    }

    @Override
    String toString() {
        "pollTime=${pollTime}, pollInterval=${pollInterval}, logContains='${logContains}'"
    }
}
