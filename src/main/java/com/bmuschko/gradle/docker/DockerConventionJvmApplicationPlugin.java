package com.bmuschko.gradle.docker;

import com.bmuschko.gradle.docker.internal.ConventionPluginHelper;
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage;
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage;
import com.bmuschko.gradle.docker.tasks.image.Dockerfile;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bmuschko.gradle.docker.internal.ConventionPluginHelper.createAppFilesCopySpec;

/**
 * The abstract class for all conventional JVM application plugins.
 *
 * @since 5.2.0
 */
public abstract class DockerConventionJvmApplicationPlugin<EXT extends DockerConventionJvmApplicationExtension> implements Plugin<Project> {

    /**
     * The task name that copies the application files to a temporary directory for image creation.
     */
    public static final String SYNC_BUILD_CONTEXT_TASK_NAME = "dockerSyncBuildContext";
    /**
     * The task name that creates the Docker image for the Java application.
     */
    public static final String DOCKERFILE_TASK_NAME = "dockerCreateDockerfile";
    /**
     * The task name that builds the Docker image for the Java application.
     */
    public static final String BUILD_IMAGE_TASK_NAME = "dockerBuildImage";
    /**
     * The task name that pushes created Docker image to the repository.
     */
    public static final String PUSH_IMAGE_TASK_NAME = "dockerPushImage";

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(DockerRemoteApiPlugin.class);
        DockerExtension dockerExtension = project.getExtensions().getByType(DockerExtension.class);
        final EXT extension = configureExtension(project.getObjects(), dockerExtension);

        project.getPlugins().withType(JavaPlugin.class).configureEach(javaPlugin -> {
            TaskProvider<Dockerfile> createDockerfileTask = registerDockerfileTask(project, extension);
            final TaskProvider<Sync> syncBuildContextTask = registerSyncBuildContextTask(project, createDockerfileTask);
            createDockerfileTask.configure(dockerfile -> dockerfile.dependsOn(syncBuildContextTask));
            TaskProvider<DockerBuildImage> dockerBuildImageTask = registerBuildImageTask(project, createDockerfileTask, extension);
            registerPushImageTask(project, dockerBuildImageTask);
        });
    }

    private TaskProvider<Dockerfile> registerDockerfileTask(final Project project, final EXT extension) {
        return project.getTasks().register(DOCKERFILE_TASK_NAME, Dockerfile.class, dockerfile -> {
            dockerfile.setGroup(DockerRemoteApiPlugin.DEFAULT_TASK_GROUP);
            dockerfile.setDescription("Creates the Docker image for the application.");
            dockerfile.from(project.provider(() -> new Dockerfile.From(extension.getBaseImage().get())));
            dockerfile.label(project.provider(() -> new HashMap<>(Map.ofEntries(Map.entry("maintainer", extension.getMaintainer().get())))));
            dockerfile.user(extension.getUser());
            dockerfile.workingDir("/app");
            dockerfile.copyFile(project.provider(() -> {
                if (new File(dockerfile.getDestDir().get().getAsFile(), "libs").isDirectory()) {
                    return new Dockerfile.CopyFile("libs", "libs/");
                }

                return null;
            }));
            dockerfile.copyFile(project.provider(() -> {
                if (ConventionPluginHelper.getMainJavaSourceSetOutput(project).getResourcesDir().isDirectory()) {
                    return new Dockerfile.CopyFile("resources", "resources/");
                }

                return null;
            }));
            dockerfile.copyFile(new Dockerfile.CopyFile("classes", "classes/"));
            dockerfile.entryPoint(project.provider(() -> {
                List<String> entrypoint = new ArrayList<>(List.of("java"));
                List<String> jvmArgs = extension.getJvmArgs().get();

                if (!jvmArgs.isEmpty()) {
                    entrypoint.addAll(jvmArgs);
                }

                entrypoint.addAll(List.of("-cp", "/app/resources:/app/classes:/app/libs/*", getApplicationMainClassName(project, extension)));

                List<String> args = extension.getArgs().get();

                if (!args.isEmpty()) {
                    entrypoint.addAll(args);
                }

                return entrypoint;
            }));
            dockerfile.exposePort(extension.getPorts());
        });
    }

    private static TaskProvider<Sync> registerSyncBuildContextTask(final Project project, final TaskProvider<Dockerfile> createDockerfileTask) {
        return project.getTasks().register(SYNC_BUILD_CONTEXT_TASK_NAME, Sync.class, sync -> {
            sync.setGroup(DockerRemoteApiPlugin.DEFAULT_TASK_GROUP);
            sync.setDescription("Copies the distribution resources to a temporary directory for image creation.");
            sync.dependsOn(project.getTasks().getByName(JavaPlugin.CLASSES_TASK_NAME));
            sync.into(createDockerfileTask.get().getDestDir());
            sync.with(createAppFilesCopySpec(project));
        });
    }

    private TaskProvider<DockerBuildImage> registerBuildImageTask(final Project project, final TaskProvider<Dockerfile> createDockerfileTask, final EXT extension) {
        return project.getTasks().register(BUILD_IMAGE_TASK_NAME, DockerBuildImage.class, dockerBuildImage -> {
            dockerBuildImage.setGroup(DockerRemoteApiPlugin.DEFAULT_TASK_GROUP);
            dockerBuildImage.setDescription("Builds the Docker image for the application.");
            dockerBuildImage.dependsOn(createDockerfileTask);
            dockerBuildImage.getImages().addAll(determineImages(project, extension));
        });
    }

    private Provider<Set<String>> determineImages(final Project project, final EXT extension) {
        return project.provider(() -> {
            if (extension.getImages().getOrNull() != null && !extension.getImages().get().isEmpty()) {
                return extension.getImages().get();
            }

            final String tagVersion = project.getVersion().equals("unspecified") ? "latest" : project.getVersion().toString();
            String artifactAndVersion = (project.getName() + ":" + tagVersion).toLowerCase();
            return Set.of(!project.getGroup().toString().isEmpty() ? project.getGroup() + "/" + artifactAndVersion : artifactAndVersion);
        });
    }

    private static void registerPushImageTask(Project project, final TaskProvider<DockerBuildImage> dockerBuildImageTask) {
        project.getTasks().register(PUSH_IMAGE_TASK_NAME, DockerPushImage.class, pushImage -> {
            pushImage.setGroup(DockerRemoteApiPlugin.DEFAULT_TASK_GROUP);
            pushImage.setDescription("Pushes created Docker image to the repository.");
            pushImage.dependsOn(dockerBuildImageTask);
            pushImage.getImages().convention(dockerBuildImageTask.get().getImages());
        });
    }

    private String getApplicationMainClassName(Project project, EXT extension) throws IOException {
        if (extension.getMainClassName().isPresent()) {
            return extension.getMainClassName().get();
        }

        for (File classesDir : ConventionPluginHelper.getMainJavaSourceSetOutput(project).getClassesDirs()) {
            String mainClassName = findMainClassName(classesDir);

            if (mainClassName != null) {
                return mainClassName;
            }
        }

        throw new IllegalStateException("Main class name could not be resolved");
    }

    protected abstract EXT configureExtension(ObjectFactory objectFactory, DockerExtension dockerExtension);

    protected abstract String findMainClassName(File classesDir) throws IOException;
}
