# Introduction
This document describes the release process designed and implemented for `gradle-docker-plugin`. Its main purpose is to explain to developers and maintainers how to prepare and release a new version of this plugin.

# Tools
The release process uses some external libraries and services described in detail below. 

## gradle-git
The [`gradle-git`](https://github.com/ajoberstar/gradle-git) Gradle plugin is used to automatically determine the project version. `org.ajoberstar.release-opinion` is applied in the main [build.gradle](build.gradle#L15) and configured in [gradle/release.gradle](gradle/release.gradle#L16). Please refer to the plugin [documentation](https://github.com/ajoberstar/gradle-git/wiki/Release%20Plugins#how-do-i-use-the-opinion-plugin) for more details.

## Travis CI
[Travis CI](https://travis-ci.com) service is used as our current [CI/CD](https://en.wikipedia.org/wiki/CI/CD) server. Build and deploy jobs are configured in [.travis.yml](.travis.yml) file. Please refer its [documentation](https://docs.travis-ci.com/) for more details.

## Bintray
[Bintray](https://bintray.com) service is used to publish plugin versions. With [BintrayPlugin](https://github.com/bintray/gradle-bintray-plugin) artifacts are uploaded to a remote reposiotry. Plugin configuration is in the [gradle/publishing.gradle](gradle/publishing.gradle) file.

# Workflow
The release process is automated to some extent. The following steps describe the workflow.
1. Developer updates `README.md` and `RELEASE_NOTES.md` with new planned version.
2. Developer commits all changes in local working copy.
3. Developer triggers new version release using the following command: `$ ./gradlew release -Prelease.stage=final -Prelease.scope=[SCOPE]`, where `[SCOPE]` can be one of: `major`, `minor`, `patch` and determines which part of the version string `<major>.<minor>.<patch>` will be increased.
4. Gradle executes a build on developer's machine which calculates new version string, creates new tag with it and pushes to the `origin`.
5. When Gradle build is finished, developer's work is done and the rest of the release process is automated.
6. After push to the `origin`, Travis detects new tag and triggers a build.
7. Travis [is instructed](.travis.yml#L23) to execute [release stage](https://docs.travis-ci.com/user/build-stages/) when on Git tag.
8. In this stage [Gradle script](.travis.yml#L21) assembles plugin binaries (with new version) and uploads them to Bintray (credentials are stored as [secure variables](https://docs.travis-ci.com/user/environment-variables/#Defining-Variables-in-Repository-Settings) in Travis). Also JavaDoc is published to `gh-pages` branch.

# Useful links
* [Semantic Versioning](http://semver.org/)
* [gradle-git version inference](https://github.com/ajoberstar/gradle-git/wiki/Release%20Plugins#version-inference)
* [Travis script deployment](https://docs.travis-ci.com/user/deployment/script/)
