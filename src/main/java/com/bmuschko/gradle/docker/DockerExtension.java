package com.bmuschko.gradle.docker;

import com.bmuschko.gradle.docker.internal.DefaultDockerConfigResolver;
import com.bmuschko.gradle.docker.internal.DefaultDockerUrlValueSource;
import com.bmuschko.gradle.docker.internal.DockerConfigResolver;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSourceParameters;

import java.io.File;

/**
 * The extension for configuring the Docker communication via the remote API through the {@link DockerRemoteApiPlugin}.
 * <p>
 * Other convention plugins like the {@link DockerJavaApplicationPlugin} and {@link DockerSpringBootApplicationPlugin} may further extend this extension as nested configuration elements.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     url = 'https://192.168.59.103:2376'
 * }
 * </pre>
 */
public class DockerExtension {

    /**
     * The server URL to connect to via Docker’s remote API.
     * <p>
     * Defaults to {@code unix:///var/run/docker.sock} for Unix systems and {@code tcp://127.0.0.1:2375} for Windows systems.
     */
    public final Property<String> getUrl() {
        return url;
    }

    private final Property<String> url;

    /**
     * The path to certificates for communicating with Docker over SSL.
     * <p>
     * Defaults to value of environment variable {@code DOCKER_CERT_PATH} if set.
     */
    public final DirectoryProperty getCertPath() {
        return certPath;
    }

    private final DirectoryProperty certPath;

    /**
     * The remote API version. For most cases this can be left null.
     */
    public final Property<String> getApiVersion() {
        return apiVersion;
    }

    private final Property<String> apiVersion;

    /**
     * The target Docker registry credentials.
     */
    public final DockerRegistryCredentials getRegistryCredentials() {
        return registryCredentials;
    }

    private final DockerRegistryCredentials registryCredentials;

    public DockerExtension(ObjectFactory objectFactory, ProviderFactory providerFactory) {
        DockerConfigResolver dockerConfigResolver = new DefaultDockerConfigResolver();

        url = objectFactory.property(String.class);
        url.convention(providerFactory.of(DefaultDockerUrlValueSource.class, noneValueSourceSpec -> {}));
        certPath = objectFactory.directoryProperty();

        File defaultDockerCert = dockerConfigResolver.getDefaultDockerCert();

        if (defaultDockerCert != null) {
            certPath.convention(objectFactory.directoryProperty().fileValue(defaultDockerCert));
        }

        apiVersion = objectFactory.property(String.class);
        registryCredentials = objectFactory.newInstance(DockerRegistryCredentials.class, objectFactory);
    }

    /**
     * Configures the target Docker registry credentials.
     *
     * @param action The action against the Docker registry credentials
     * @since 6.0.0
     */
    public void registryCredentials(Action<? super DockerRegistryCredentials> action) {
        action.execute(registryCredentials);
    }
}
