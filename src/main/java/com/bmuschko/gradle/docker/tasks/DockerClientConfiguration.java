package com.bmuschko.gradle.docker.tasks;

import org.gradle.api.file.Directory;

public class DockerClientConfiguration {
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Directory getCertPath() {
        return certPath;
    }

    public void setCertPath(Directory certPath) {
        this.certPath = certPath;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    private String url;
    private Directory certPath;
    private String apiVersion;

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !getClass().equals(o.getClass())) return false;

        DockerClientConfiguration that = (DockerClientConfiguration) o;

        if (!apiVersion.equals(that.getApiVersion())) return false;
        if (!certPath.equals(that.getCertPath())) return false;
        if (!url.equals(that.getUrl())) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (url != null ? url.hashCode() : 0);
        result = 31 * result + (certPath != null ? certPath.hashCode() : 0);
        result = 31 * result + (apiVersion != null ? apiVersion.hashCode() : 0);
        return result;
    }
}
