dependencyResolutionManagement {
    versionCatalogs {
        create("buildsrcLibs") {
            from(files("../gradle/buildsrc.libs.versions.toml"))
        }
    }
}