### Version 3.0.5 (TBA)

* Support multiple variables per singled ENV cmd - [Pull request 311](https://github.com/bmuschko/gradle-docker-plugin/pull/311)

### Version 3.0.4 (December 1, 2016)

* Implement [reactive-stream](https://github.com/reactive-streams/reactive-streams-jvm/#2-subscriber-code) method `onError` for all tasks - [Pull request 302](https://github.com/bmuschko/gradle-docker-plugin/pull/302)
* Bump docker-java to 3.0.6 - [Pull request 279](https://github.com/bmuschko/gradle-docker-plugin/pull/279)

### Version 3.0.3 (September 6, 2016)

* Print error messages received from docker engine when build fails - [Pull request 265](https://github.com/bmuschko/gradle-docker-plugin/pull/265)
* Bump docker-java to 3.0.5 - [Pull request 263](https://github.com/bmuschko/gradle-docker-plugin/pull/263)
* Add support for `force` removal on `DockerRemoveImage` - [Pull request 266](https://github.com/bmuschko/gradle-docker-plugin/pull/266)
* Various fixes and cleanups as well default to alpine image for all functional tests - [Pull request 269](https://github.com/bmuschko/gradle-docker-plugin/pull/269)
* Added `editorconfig` file with some basic defaults - [Pull request 270](https://github.com/bmuschko/gradle-docker-plugin/pull/270)

### Version 3.0.2 (August 14, 2016)

* Add support for build-time variables in `DockerBuildImage` task - [Pull request 240](https://github.com/bmuschko/gradle-docker-plugin/pull/240)
* Fix incorrect docker-java method name in `DockerCreateContainer` task - [Pull request 242](https://github.com/bmuschko/gradle-docker-plugin/pull/242)
* Can define devices on `DockerCreateContainer` task - [Pull request 245](https://github.com/bmuschko/gradle-docker-plugin/pull/245)
* Can now supply multiple ports when working with `docker-java-application` - [Pull request 254](https://github.com/bmuschko/gradle-docker-plugin/pull/254)
* Bump docker-java to 3.0.2 - [Pull request 259](https://github.com/bmuschko/gradle-docker-plugin/pull/259)
* If buildscript repos are required make sure they are added after evaluation - [Pull request 260](https://github.com/bmuschko/gradle-docker-plugin/pull/260)

### Version 3.0.1 (July 6, 2016)

* Simplify Gradle TestKit usage - [Pull request 225](https://github.com/bmuschko/gradle-docker-plugin/pull/225)
* Ensure `tlsVerify` is set in addition to `certPath` for DockerClientConfig setup - [Pull request 230](https://github.com/bmuschko/gradle-docker-plugin/pull/230)
* Upgrade to Gradle 2.14.

### Version 3.0.0 (June 5, 2016)

* Task `DockerLogsContainer` gained attribute `sink` - [Pull request 203](https://github.com/bmuschko/gradle-docker-plugin/pull/203)
* Task `DockerBuildImage` will no longer insert extra newline as part of build output - [Pull request 206](https://github.com/bmuschko/gradle-docker-plugin/pull/206)
* Upgrade to docker-java 3.0.0 - [Pull request 217](https://github.com/bmuschko/gradle-docker-plugin/pull/217)
* Fallback to buildscript.repositories for internal dependency resolution if no repositories were defined - [Pull request 218](https://github.com/bmuschko/gradle-docker-plugin/pull/218)
* Added task `DockerExecContainer` - [Pull request 221](https://github.com/bmuschko/gradle-docker-plugin/pull/221)
* Added task `DockerCopyFileToContainer` - [Pull request 222](https://github.com/bmuschko/gradle-docker-plugin/pull/222)
* Task `DockerCreateContainer` gained attribute `restartPolicy` - [Pull request 224](https://github.com/bmuschko/gradle-docker-plugin/pull/224)
* Remove use of Gradle internal methods.
* Added ISSUES.md file.
* Upgrade to Gradle 2.13.

### Version 2.6.8 (April 10, 2016)
* Added task `DockerLogsContainer` - [Pull request 181](https://github.com/bmuschko/gradle-docker-plugin/pull/181)
* Bump docker-java to version 2.3.3 - [Pull request 183](https://github.com/bmuschko/gradle-docker-plugin/pull/183)
* Bug fix when not checking if parent dir already exists before creating with `DockerCopyFileToContainer` - [Pull request 186](https://github.com/bmuschko/gradle-docker-plugin/pull/186)
* `DockerWaitContainer` now produces exitCode - [Pull request 189](https://github.com/bmuschko/gradle-docker-plugin/pull/189)
* `apiVersion` can now be set on `DockerExtension` and overriden on all tasks - [Pull request 182](https://github.com/bmuschko/gradle-docker-plugin/pull/182)
* Internal fix where task variables had to be defined - [Pull request 194](https://github.com/bmuschko/gradle-docker-plugin/pull/194)

### Version 2.6.7 (March 10, 2016)

* Upgrade to Gradle 2.11.
* Bug fix when copying single file from container and hostPath is set to directory for `DockerCopyFileFromContainer` - [Pull request 163](https://github.com/bmuschko/gradle-docker-plugin/pull/163)
* Step reports are now printed to stdout by default for `DockerBuildImage` - [Pull request 145](https://github.com/bmuschko/gradle-docker-plugin/pull/145)
* UP-TO-DATE functionality has been removed from `DockerBuildImage` as there were too many corner cases to account for - [Pull request 172](https://github.com/bmuschko/gradle-docker-plugin/pull/172)

### Version 2.6.6 (February 27, 2016)

* Added docker step reports for `DockerBuildImage` - [Pull request 145](https://github.com/bmuschko/gradle-docker-plugin/pull/145)
* Added `onlyIf` check for `DockerBuildImage` - [Pull request 139](https://github.com/bmuschko/gradle-docker-plugin/pull/139)
* Added method logConfig for `DockerCreateContainer` - [Pull request 157](https://github.com/bmuschko/gradle-docker-plugin/pull/157)
* Various commands can now be passed closures for `Dockerfile` - [Pull request 155](https://github.com/bmuschko/gradle-docker-plugin/pull/155)
* Fix implementation of exposedPorts for `DockerCreateContainer` - [Pull request 140](https://github.com/bmuschko/gradle-docker-plugin/pull/140)
* Upgrade to Docker Java 2.2.2 - [Pull request 158](https://github.com/bmuschko/gradle-docker-plugin/pull/158).

### Version 2.6.5 (January 16, 2016)

* Fix implementation of `DockerCopyFileFromContainer` - [Pull request 135](https://github.com/bmuschko/gradle-docker-plugin/pull/135).
* Add `networkMode` property to `DockerCreateContainer` - [Pull request 114](https://github.com/bmuschko/gradle-docker-plugin/pull/114).
* Upgrade to Docker Java 2.1.4 - [Issue 138](https://github.com/bmuschko/gradle-docker-plugin/issues/138).

### Version 2.6.4 (December 24, 2015)

* Expose privileged property on `DockerCreateContainer` - [Pull request 130](https://github.com/bmuschko/gradle-docker-plugin/pull/130).

### Version 2.6.3 (December 23, 2015)

* Expose force and removeVolumes properties on `DockerRemoveContainer` - [Pull request 129](https://github.com/bmuschko/gradle-docker-plugin/pull/129).

### Version 2.6.2 (December 22, 2015)

* Expose support for LogDriver on `DockerCreateContainer` - [Pull request 118](https://github.com/bmuschko/gradle-docker-plugin/pull/118).
* Upgrade to Docker Java 2.1.2.

### Version 2.6.1 (September 21, 2015)

* Correct the `withVolumesFrom` call on `DockerCreateContainer` task which needs to get a `VolumesFrom[]` array as the parameter - [Pull request 102](https://github.com/bmuschko/gradle-docker-plugin/pull/102).
* Upgrade to Docker Java 2.1.1 - [Pull request 109](https://github.com/bmuschko/gradle-docker-plugin/pull/109).

### Version 2.6 (August 30, 2015)

* Upgrade to Docker Java 2.1.0 - [Pull request 92](https://github.com/bmuschko/gradle-docker-plugin/pull/92).
_Note:_ The Docker Java API changed vastly with version 2.0.0. The tasks `DockerBuildImage`, `DockerPullImage` and 
`DockerPushImage` do not provide a response handler anymore. This is a breaking change. Future versions of the plugin
might open up the response handling again in some way.
* `DockerListImages` with `filter` call a wrong function from `ListImagesCmdImpl.java` - [Issue 105](https://github.com/bmuschko/gradle-docker-plugin/issues/105).

### Version 2.5.2 (August 15, 2015)

* Fix listImages task throwing GroovyCastException - [Issue 96](https://github.com/bmuschko/gradle-docker-plugin/issues/96).
* Add support for publishAll in DockerCreateContainer - [Pull request 94](https://github.com/bmuschko/gradle-docker-plugin/pull/94).
* Add optional dockerFile option to the DockerBuildImage task - [Pull request 47](https://github.com/bmuschko/gradle-docker-plugin/pull/47).

### Version 2.5.1 (July 29, 2015)

* Adds Dockerfile support for the LABEL instruction - [Pull request 86](https://github.com/bmuschko/gradle-docker-plugin/pull/86).
* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 1.4.0. Underlying API does not provide
setting port bindings for task `DockerStartContainer` anymore. Needs to be set on `DockerCreateContainer`.

### Version 2.5 (July 18, 2015)

* Expose response handler for `DockerListImages` task - [Issue 75](https://github.com/bmuschko/gradle-docker-plugin/issues/75).
* Pass in credentials when building an image - [Issue 76](https://github.com/bmuschko/gradle-docker-plugin/issues/76).

### Version 2.4.1 (July 4, 2015)

* Add `extraHosts` property to task `DockerCreateContainer` - [Pull request 79](https://github.com/bmuschko/gradle-docker-plugin/pull/79).
* Add `pull` property to task `DockerBuildImage` - [Pull request 78](https://github.com/bmuschko/gradle-docker-plugin/pull/78).

### Version 2.4 (May 16, 2015)

* Added missing support for properties `portBindings` and `cpuset` in `CreateContainer` - [Pull request 66](https://github.com/bmuschko/gradle-docker-plugin/pull/66).
* Expose response handlers so users can inject custom handling logic - [Issue 65](https://github.com/bmuschko/gradle-docker-plugin/issues/65).
* Upgrade to Gradle 2.4 including all compatible plugins and libraries.

### Version 2.3.1 (April 25, 2015)

* Added support for `Binds` when creating containers - [Pull request 54](https://github.com/bmuschko/gradle-docker-plugin/pull/54).
* Added task for copying files from a container to a host - [Pull request 57](https://github.com/bmuschko/gradle-docker-plugin/pull/57).

### Version 2.3 (April 18, 2015)

* Added task `DockerInspectContainer` - [Pull request 44](https://github.com/bmuschko/gradle-docker-plugin/pull/44).
* Added property `containerName` to task `DockerCreateContainer` - [Pull request 44](https://github.com/bmuschko/gradle-docker-plugin/pull/44).
* Allow for linking containers for task `DockerCreateContainer` - [Pull request 53](https://github.com/bmuschko/gradle-docker-plugin/pull/53).
* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 1.2.0.

### Version 2.2 (April 12, 2015)

* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 1.1.0.

### Version 2.1 (March 24, 2015)

* Renamed property `registry` to `registryCredentials` for plugin extension and tasks implementing `RegistryCredentialsAware` to better indicate its purpose.
_Note:_ This is a breaking change.

### Version 2.0.3 (March 20, 2015)

* Allow for specifying port bindings for container start command. - [Issue 30](https://github.com/bmuschko/gradle-docker-plugin/issues/30).
* Throw an exception if an error response is encountered - [Issue 37](https://github.com/bmuschko/gradle-docker-plugin/issues/37).
* Upgrade to Gradle 2.3.

### Version 2.0.2 (February 19, 2015)

* Set source and target compatibility to Java 6 - [Issue 32](https://github.com/bmuschko/gradle-docker-plugin/issues/32).

### Version 2.0.1 (February 10, 2015)

* Extension configuration method for `DockerJavaApplicationPlugin` needs to be registered via extension instance - [Issue 28](https://github.com/bmuschko/gradle-docker-plugin/issues/28).

### Version 2.0 (February 4, 2015)

* Upgrade to Gradle 2.2.1 including all compatible plugins and libraries.

### Version 0.8.3 (February 4, 2015)

* Add project group to default tag built by Docker Java application plugin - [Issue 25](https://github.com/bmuschko/gradle-docker-plugin/issues/25).

### Version 0.8.2 (January 30, 2015)

* Expose method for task `Dockerfile` for providing vanilla Docker instructions.

### Version 0.8.1 (January 24, 2015)

* Usage of [docker-java library](https://github.com/docker-java/docker-java) version 0.10.5.
* Correctly create model instances for create container task - [Issue 19](https://github.com/bmuschko/gradle-docker-plugin/issues/19).

### Version 0.8 (January 7, 2014)

* Allow for pushing to Docker Hub - [Issue 18](https://github.com/bmuschko/gradle-docker-plugin/issues/18).
* Better handling of API responses.
* Note: Change to plugin extension. The property `docker.serverUrl` is now called `docker.url`. Instead of `docker.credentials`, you will need to use `docker.registry`.

### Version 0.7.2 (December 23, 2014)

* `Dockerfile` task is always marked UP-TO-DATE after first execution - [Issue 13](https://github.com/bmuschko/gradle-docker-plugin/issues/13).
* Improvements to `Dockerfile` task - [Pull request 16](https://github.com/bmuschko/gradle-docker-plugin/pull/16).
    * Fixed wrong assignment of key field in  environment variable instruction.
    * Allow for providing multiple ports to the expose instruction.

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
