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

package com.bmuschko.gradle.docker.domain;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

/**
 * Class holding metadata for an arbitrary copy-file-to-container invocation.
 */
public class CopyFileToContainer {

    /**
     * The host path.
     * <p>
     * Can take the form of {@code String}, {@code GString}, {@code File}, or {@code Closure} which returns any of the previous.
     */
    @Input
    private Object hostPath;

    public Object getHostPath() {
        return hostPath;
    }

    public void setHostPath(Object hostPath) {
        this.hostPath = hostPath;
    }

    /**
     * The remote path.
     * <p>
     * Can take the form of {@code String}, {@code GString}, {@code File}, or {@code Closure} which returns any of the previous.
     */
    @Input
    private Object remotePath;

    public Object getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(Object remotePath) {
        this.remotePath = remotePath;
    }

    /**
     * Indicates if copied file is a TAR file.
     */
    @Internal
    private boolean isTar = false;

    public boolean getIsTar() {
        return isTar;
    }

    public boolean isIsTar() {
        return isTar;
    }

    public void setIsTar(boolean isTar) {
        this.isTar = isTar;
    }

}
