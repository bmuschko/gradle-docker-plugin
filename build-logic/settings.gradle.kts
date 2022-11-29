dependencyResolutionManagement {
    versionCatalogs {
        create("buildsrclibs") {
            from(files("../gradle/buildsrc.libs.versions.toml"))
        }
    }
}

rootProject.name = "gradle-docker-plugin-build-logic"
