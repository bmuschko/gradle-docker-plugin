plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(buildsrclibs.asciidoctor.gradle.plugin)
    runtimeOnly(buildsrclibs.asciidoctorj.tabbed.code.extension)
    implementation(buildsrclibs.grgit) {
        setForce(true)
    }
    implementation(buildsrclibs.gradle.git)
    implementation(buildsrclibs.gradle.git.publish)
    implementation(buildsrclibs.shadow)
}

gradlePlugin {
    plugins {
        register("test-setup-plugin") {
            id = "com.bmuschko.gradle.docker.test-setup"
            implementationClass = "com.bmuschko.gradle.docker.TestSetupPlugin"
        }
        register("integration-test-plugin") {
            id = "com.bmuschko.gradle.docker.integration-test"
            implementationClass = "com.bmuschko.gradle.docker.IntegrationTestPlugin"
        }
        register("functional-test-plugin") {
            id = "com.bmuschko.gradle.docker.functional-test"
            implementationClass = "com.bmuschko.gradle.docker.FunctionalTestPlugin"
        }
        register("doc-test-plugin") {
            id = "com.bmuschko.gradle.docker.doc-test"
            implementationClass = "com.bmuschko.gradle.docker.DocumentationTestPlugin"
        }
        register("additional-artifacts-plugin") {
            id = "com.bmuschko.gradle.docker.additional-artifacts"
            implementationClass = "com.bmuschko.gradle.docker.AdditionalArtifactsPlugin"
        }
        register("shaded-artifacts-plugin") {
            id = "com.bmuschko.gradle.docker.shaded-artifacts"
            implementationClass = "com.bmuschko.gradle.docker.ShadedArtifactsPlugin"
        }
        register("user-guide-plugin") {
            id = "com.bmuschko.gradle.docker.user-guide"
            implementationClass = "com.bmuschko.gradle.docker.UserGuidePlugin"
        }
        register("documentation-plugin") {
            id = "com.bmuschko.gradle.docker.documentation"
            implementationClass = "com.bmuschko.gradle.docker.DocumentationPlugin"
        }
        register("release-plugin") {
            id = "com.bmuschko.gradle.docker.release"
            implementationClass = "com.bmuschko.gradle.docker.ReleasePlugin"
        }
    }
}