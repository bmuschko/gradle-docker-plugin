package com.bmuschko.gradle.docker.tasks

import groovy.transform.CompileStatic
import org.gradle.api.file.Directory

@CompileStatic
class DockerClientConfiguration {
    private String url;
    private Directory certPath;
    private String apiVersion;

    String getUrl() {
        return url
    }

    void setUrl(String url) {
        this.url = url
    }

    Directory getCertPath() {
        return certPath
    }

    void setCertPath(Directory certPath) {
        this.certPath = certPath
    }

    String getApiVersion() {
        return apiVersion
    }

    void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.class) return false

        DockerClientConfiguration that = (DockerClientConfiguration) o

        if (apiVersion != that.apiVersion) return false
        if (certPath != that.certPath) return false
        if (url != that.url) return false

        return true
    }

    int hashCode() {
        int result
        result = (url != null ? url.hashCode() : 0)
        result = 31 * result + (certPath != null ? certPath.hashCode() : 0)
        result = 31 * result + (apiVersion != null ? apiVersion.hashCode() : 0)
        return result
    }
}
