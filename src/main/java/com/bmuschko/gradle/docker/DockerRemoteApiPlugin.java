package com.bmuschko.gradle.docker;

import com.bmuschko.gradle.docker.internal.services.DockerClientService;
import com.bmuschko.gradle.docker.tasks.AbstractDockerRemoteApiTask;
import com.bmuschko.gradle.docker.tasks.RegistryCredentialsAware;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceSpec;

/**
 * Gradle plugin that provides custom tasks for interacting with Docker via its remote API.
 * <p>
 * Exposes the extension {@link DockerExtension} required to configure the communication and authentication with the Docker remote API. Provides Docker registry credential values from the extension to all custom tasks that implement {@link RegistryCredentialsAware}.
 */
public class DockerRemoteApiPlugin implements Plugin<Project> {

    /**
     * The name of the extension.
     */
    public static final String EXTENSION_NAME = "docker";
    /**
     * The group for all tasks created by this plugin.
     */
    public static final String DEFAULT_TASK_GROUP = "Docker";

    @Override
    public void apply(Project project) {
        final DockerExtension dockerExtension = project.getExtensions().create(EXTENSION_NAME, DockerExtension.class, project.getObjects(), project.getProviders());
        configureRegistryCredentialsAwareTasks(project, dockerExtension.getRegistryCredentials());

        final Provider<DockerClientService> serviceProvider = project.getGradle().getSharedServices().registerIfAbsent("docker", DockerClientService.class, new Action<BuildServiceSpec<DockerClientService.Params>>() {
            @Override
            public void execute(BuildServiceSpec<DockerClientService.Params> pBuildServiceSpec) {
                pBuildServiceSpec.parameters(parameters -> {
                    parameters.getUrl().set(dockerExtension.getUrl());
                    parameters.getCertPath().set(dockerExtension.getCertPath());
                    parameters.getApiVersion().set(dockerExtension.getApiVersion());
                    parameters.getBuildKit().set(dockerExtension.getBuildKit());
                });
            }
        });

        project.getTasks().withType(AbstractDockerRemoteApiTask.class).configureEach(new Action<AbstractDockerRemoteApiTask>() {
            @Override
            public void execute(AbstractDockerRemoteApiTask task) {
                task.getDockerClientService().set(serviceProvider);
            }
        });

        project.getTasks().withType(DockerBuildImage.class).configureEach(new Action<DockerBuildImage>() {
            @Override
            public void execute(DockerBuildImage task) {
                task.getBuildKit().convention(dockerExtension.getBuildKit());
            }
        });
    }

    private void configureRegistryCredentialsAwareTasks(Project project, final DockerRegistryCredentials extensionRegistryCredentials) {
        project.getTasks().withType(RegistryCredentialsAware.class).configureEach(new Action<RegistryCredentialsAware>() {
            @Override
            public void execute(RegistryCredentialsAware task) {
                task.getRegistryCredentials().getUrl().convention(extensionRegistryCredentials.getUrl());
                task.getRegistryCredentials().getUsername().convention(extensionRegistryCredentials.getUsername());
                task.getRegistryCredentials().getPassword().convention(extensionRegistryCredentials.getPassword());
                task.getRegistryCredentials().getEmail().convention(extensionRegistryCredentials.getEmail());
            }
        });
    }
}
