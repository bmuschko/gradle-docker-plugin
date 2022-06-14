dependencyResolutionManagement {
    versionCatalogs {
        create("buildsrclibs") {
            from(files("../gradle/buildsrc.libs.versions.toml"))
        }
    }
}