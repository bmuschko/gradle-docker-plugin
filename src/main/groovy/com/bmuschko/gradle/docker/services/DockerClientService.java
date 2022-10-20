package com.bmuschko.gradle.docker.services;

import com.bmuschko.gradle.docker.tasks.DockerClientConfiguration;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.google.common.io.Closer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DockerClientService implements BuildService<DockerClientService.Params>, AutoCloseable {
    private final Map<DefaultDockerClientConfig, DockerClient> dockerClients;

    private final ObjectFactory objects;

    public interface Params extends BuildServiceParameters {
        Property<String> getUrl();
        DirectoryProperty getCertPath();
        Property<String> getApiVersion();
    }

    @Inject
    public DockerClientService(ObjectFactory objects) {
        this.objects = objects;
        dockerClients = new ConcurrentHashMap<>();
    }

    public DockerClient getDockerClient(DockerClientConfiguration dockerClientConfiguration) {
        String dockerUrl = getDockerHostUrl(dockerClientConfiguration);
        File dockerCertPath = thingOrProperty(objects.directoryProperty(), dockerClientConfiguration.getCertPath(), getParameters().getCertPath()).map(Directory::getAsFile).getOrNull();
        String apiVersion = thingOrProperty(objects.property(String.class), dockerClientConfiguration.getApiVersion(), getParameters().getApiVersion()).getOrNull();

        // Create configuration
        DefaultDockerClientConfig.Builder dockerClientConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        dockerClientConfigBuilder.withDockerHost(dockerUrl);

        if (dockerCertPath != null) {
            String caninicalCertPath;
            try {
                caninicalCertPath = dockerCertPath.getCanonicalPath();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            dockerClientConfigBuilder.withDockerTlsVerify(true);
            dockerClientConfigBuilder.withDockerCertPath(caninicalCertPath);
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
     * @param dockerClientConfiguration docker client configuration
     * @return Docker host URL as string
     */
    private String getDockerHostUrl(DockerClientConfiguration dockerClientConfiguration) {
        String url = thingOrProperty(objects.property(String.class), dockerClientConfiguration.getUrl(), getParameters().getUrl()).map(String::toLowerCase).get();
        return url.startsWith("http") ? "tcp" + url.substring(url.indexOf(':')) : url;
    }

    private <T> Provider<T> thingOrProperty(Property<T> prop, T t, Property<T> tp) {
        prop.set(t);
        return prop.orElse(tp);
    }

    @Override
    public void close() throws Exception {
        try (final Closer closer = Closer.create()) {
            dockerClients.values().forEach(closer::register);
        }
    }
}
