### Version 0.7.2 (December 23, 2014)

* `Dockerfile` task is always marked UP-TO-DATE after first execution - [Issue 13](https://github.com/bmuschko/gradle-docker-plugin/issues/13).

### Version 0.7.1 (December 16, 2014)

* Fixed entry point definition of Dockerfile set by Java application plugin.

### Version 0.7 (December 14, 2014)

* Allow for properly add user-based instructions to Dockfile task with predefined instructions without messing up the order. - [Issue 12](https://github.com/bmuschko/gradle-docker-plugin/issues/12).
* Renamed task `dockerCopyDistTar` to `dockerCopyDistResources` to better express intent.

### Version 0.6.1 (December 11, 2014)

* Allow for setting path to certificates for communicating with Docker over SSL - [Issue 10](https://github.com/bmuschko/gradle-docker-plugin/issues/10).

### Version 0.6 (December 7, 2014)

* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 0.10.4.
* Added Docker Java application plugin.
* Better documentation.

### Version 0.5 (December 6, 2014)

* Fixed implementations of tasks `DockerPushImage` and `DockerCommitImage` - [Issue 11](https://github.com/bmuschko/gradle-docker-plugin/issues/11).

### Version 0.4 (November 27, 2014)

* Added task for creating a Dockerfile.

### Version 0.3 (November 23, 2014)

* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 0.10.3.
* Changed package name to `com.bmuschko.gradle.docker`.
* Changed group ID to `com.bmuschko`.
* Adapted plugin IDs to be compatible with Gradle's plugin portal.

### Version 0.2 (June 19, 2014)

* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 0.8.2.
* Provide custom task type for push operation.
* Support for using remote URLs when building image - [Issue 3](https://github.com/bmuschko/gradle-docker-plugin/issues/3).

### Version 0.1 (May 11, 2014)

* Initial release.