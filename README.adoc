image:https://github.com/bmuschko/gradle-docker-plugin/workflows/Build%20and%20Release%20%5BLinux%5D/badge.svg["Build Status", link="https://github.com/bmuschko/gradle-docker-plugin/actions?query=workflow%3A%22Build+and+Release+%5BLinux%5D%22"]
image:https://github.com/bmuschko/gradle-docker-plugin/workflows/Build%20%5BWindows%5D/badge.svg["Build Status", link="https://github.com/bmuschko/gradle-docker-plugin/actions?query=workflow%3A%22Build+%5BWindows%5D%22"]
image:https://img.shields.io/badge/user%20guide-latest-red["User Guide", link="https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/"]
image:https://img.shields.io/badge/dev%20guide-latest-orange["Developer Guide", link="https://bmuschko.github.io/gradle-docker-plugin/current/dev-guide/"]
image:https://img.shields.io/badge/api%20doc-latest-blue["API Doc", link="https://bmuschko.github.io/gradle-docker-plugin/current/api/"]

= Gradle Docker plugin

++++
<table border=1>
    <tr>
        <td>
            Over the past couple of years this plugin has seen many releases. Our core committers and contributors have done an amazing job! Sometimes life can get in the way of Open Source leading to less frequent releases and slower response times on issues.
        </td>
    </tr>
    <tr>
        <td>
            We are actively looking for additional committers that can drive the direction of the functionality and are willing to take on maintenance and implementation of the project. If you are interested, shoot me a <a href="mailto:benjamin.muschko@gmail.com">mail</a>. We'd love to hear from you!
        </td>
    </tr>
</table>
++++

Gradle plugin for managing link:https://www.docker.io/[Docker] images and containers using the
link:http://docs.docker.io/reference/api/docker_remote_api/[Docker remote API]. The heavy lifting of communicating with the
Docker remote API is handled by the link:https://github.com/docker-java/docker-java[Docker Java library]. Please
refer to the library's documentation for more information on the supported Docker's client API and Docker server version.

== Documentation

* Read the https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/[user guide].
* Want to contribute? Have a look at the https://bmuschko.github.io/gradle-docker-plugin/current/dev-guide/[dev guide].
* Skim through the https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/#change_log[release notes].
* Check out the full-fledged https://github.com/bmuschko/gradle-docker-plugin/tree/master/src/docs/samples/code[sample projects] using the Groovy and the Kotlin DSL.
* Inspect classes and methods of the plugin in the https://bmuschko.github.io/gradle-docker-plugin/current/api/[API documentation].
* Anything unclear? Ask a question on the https://gitter.im/gradle-docker-plugin/Lobby[Gitter channel] or https://github.com/bmuschko/gradle-docker-plugin/issues[open an issue].
