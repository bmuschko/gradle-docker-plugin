plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation("org.asciidoctor:asciidoctor-gradle-plugin:1.5.7")
    implementation("org.ajoberstar:gradle-git:1.7.1")
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
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
        register("additional-artifacts-plugin") {
            id = "com.bmuschko.gradle.docker.additional-artifacts"
            implementationClass = "com.bmuschko.gradle.docker.AdditionalArtifactsPlugin"
        }
        register("static-code-analysis-plugin") {
            id = "com.bmuschko.gradle.docker.static-code-analysis"
            implementationClass = "com.bmuschko.gradle.docker.StaticCodeAnalysisPlugin"
        }
        register("user-guide-plugin") {
            id = "com.bmuschko.gradle.docker.user-guide"
            implementationClass = "com.bmuschko.gradle.docker.UserGuidePlugin"
        }
        register("release-plugin") {
            id = "com.bmuschko.gradle.docker.release"
            implementationClass = "com.bmuschko.gradle.docker.ReleasePlugin"
        }
    }
}