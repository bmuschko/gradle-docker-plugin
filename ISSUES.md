# gradle-docker-plugin Issues

## Read through the documentation

Before submitting an issue ensure you've done the following:
* Read through our [documentation](http://bmuschko.github.io/gradle-docker-plugin/docs/userguide/) making sure you understand the tasks you're using.
* Read through our [groovydocs](http://bmuschko.github.io/gradle-docker-plugin/docs/groovydoc/) for a more detailed explanation of how things work. 


## Try troubleshooting docker

Here are a few things you can try to help diagnose the problem yourself:
* Can you reproduce the issue outside of the plugin?
* Have you looked at the docker daemon logs themselves (i.e. systemctl status docker) to see if anything popped?


## Submitting an ISSUE

If you've made it this far then the assumption is that you've read through our documentation and tried troubleshooting the problem but to no avail.

First check to see if your [issue](https://github.com/bmuschko/gradle-docker-plugin/issues) has been previously reported on. 

Because we build on top of the [docker-java](https://github.com/docker-java/docker-java) library check also to see if [your issue](https://github.com/docker-java/docker-java/issues) has been reported on with that project as well. 

If neither of the above then please submit an ISSUE in the following format:

    plugin-version: 2.6.9
    gradle-version: 2.14
    docker-version: 1.11
    platform: ubuntu linux
    description: terse explanation of the problem
    file: copy/paste build.gradle file which can reproduce the problem