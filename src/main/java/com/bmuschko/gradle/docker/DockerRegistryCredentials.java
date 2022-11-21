package com.bmuschko.gradle.docker;

import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * The extension for configuring the Docker communication via the remote API through the {@link DockerRemoteApiPlugin}.
 * <p>
 * The following example demonstrates the use of the extension in a build script using the Groovy DSL:
 * <pre>
 * docker {
 *     registryCredentials {
 *         username = 'bmuschko'
 *         password = 'pwd'
 *     }
 * }
 * </pre>
 */
public class DockerRegistryCredentials {

    /**
     * The registry URL used as default value for the property {@link #url}.
     */
    public static final String DEFAULT_URL = "https://index.docker.io/v1/";
    /**
     * Registry URL needed to push images.
     * <p>
     * Defaults to "https://index.docker.io/v1/".
     */
    @Input
    public final Property<String> getUrl() {
        return url;
    }

    private final Property<String> url;

    /**
     * Registry username needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    public final Property<String> getUsername() {
        return username;
    }

    private final Property<String> username;
    /**
     * Registry password needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    public final Property<String> getPassword() {
        return password;
    }

    private final Property<String> password;

    /**
     * Registry email address needed to push images.
     * <p>
     * Defaults to null.
     */
    @Input
    @Optional
    public final Property<String> getEmail() {
        return email;
    }

    private final Property<String> email;

    @Inject
    public DockerRegistryCredentials(ObjectFactory objectFactory) {
        url = objectFactory.property(String.class);
        url.convention(DEFAULT_URL);
        username = objectFactory.property(String.class);
        password = objectFactory.property(String.class);
        email = objectFactory.property(String.class);
    }

    /**
     * Translates the Docker registry credentials into a {@link PasswordCredentials}.
     *
     * @since 4.0.0
     */
    public PasswordCredentials asPasswordCredentials() {
        return new PasswordCredentials() {
            @Override
            public String getUsername() {
                return DockerRegistryCredentials.this.getUsername().get();
            }

            @Override
            public void setUsername(@Nullable String userName) {
                DockerRegistryCredentials.this.getUsername().set(userName);
            }

            @Override
            public String getPassword() {
                return DockerRegistryCredentials.this.getPassword().get();
            }

            @Override
            public void setPassword(@Nullable String password) {
                DockerRegistryCredentials.this.getPassword().set(password);
            }

        };
    }
}
