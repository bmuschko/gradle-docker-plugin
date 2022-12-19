package com.bmuschko.gradle.docker.internal.services;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build service for Docker client.
 */
public abstract class DockerClientService implements BuildService<DockerClientService.Params>, AutoCloseable {
    private final Map<DefaultDockerClientConfig, DockerClient> dockerClients;

    private final ObjectFactory objects;

    /**
     * Parameters for build service.
     */
    public interface Params extends BuildServiceParameters {
        /**
         * The server URL to connect to via Docker’s remote API.
         *
         * @return The server URL
         */
        Property<String> getUrl();

        /**
         * The path to certificates for communicating with Docker over SSL.
         *
         * @return The cert path
         */
        DirectoryProperty getCertPath();

        /**
         * The remote API version.
         *
         * @return The remote API
         */
        Property<String> getApiVersion();
    }

    /**
     * Constructor for Docker client service.
     *
     * @param objects The object factory
     */
    @Inject
    public DockerClientService(ObjectFactory objects) {
        this.objects = objects;
        dockerClients = new ConcurrentHashMap<>();
    }

    /**
     * Returns the Docker client.
     *
     * @param urlProvider Docker client url
     * @param certPathProvider Docker client certificate path
     * @param apiVersionProvider Docker client api version
     * @return Docker client
     */
    public DockerClient getDockerClient(Provider<String> urlProvider, Provider<Directory> certPathProvider, Provider<String> apiVersionProvider) {
        String dockerUrl = getDockerHostUrl(urlProvider);
        File dockerCertPath = certPathProvider.orElse(getParameters().getCertPath()).map(Directory::getAsFile).getOrNull();
        String apiVersion = apiVersionProvider.orElse(getParameters().getApiVersion()).getOrNull();

        // Create configuration
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerClientConfigBuilder.withDockerHost(dockerUrl);

        if (dockerCertPath != null) {
            String canonicalCertPath;
            try {
                canonicalCertPath = dockerCertPath.getCanonicalPath();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            dockerClientConfigBuilder.withDockerTlsVerify(true);
            dockerClientConfigBuilder.withDockerCertPath(canonicalCertPath);
        } else {
            dockerClientConfigBuilder.withDockerTlsVerify(false);
        }

        if (apiVersion != null) {
            dockerClientConfigBuilder.withApiVersion(apiVersion);
        }

        DefaultDockerClientConfig dockerClientConfig = dockerClientConfigBuilder.build();
        return createDefaultDockerClient(dockerClientConfig);
    }

    private DockerClient createDefaultDockerClient(DefaultDockerClientConfig config) {
        return dockerClients.computeIfAbsent(config, i -> {
            ApacheDockerHttpClient dockerClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();
            return DockerClientImpl.getInstance(
                    config,
                    dockerClient
            );
        });
    }

    /**
     * Checks if Docker host URL starts with http(s) and if so, converts it to tcp
     * which is accepted by docker-java library.
     *
     * @param urlProvider Docker client url
     * @return Docker host URL as string
     */
    private String getDockerHostUrl(Provider<String> urlProvider) {
        String url = urlProvider.orElse(getParameters().getUrl()).map(String::toLowerCase).get();
        return url.startsWith("http") ? "tcp" + url.substring(url.indexOf(':')) : url;
    }

    @Override
    public void close() throws Exception {
        IOException throwable = null;
        for (DockerClient dockerClient : dockerClients.values()) {
            try {
                dockerClient.close();
            } catch (IOException e) {
                if (throwable == null) {
                    throwable = e;
                } else {
                    throwable.addSuppressed(e);
                }
            }
        }
        if (throwable != null) {
            throw throwable;
        }
    }
}
