package com.bmuschko.gradle.docker

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import java.text.SimpleDateFormat
import java.util.*

class PublishingPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        applyPublishingPlugin()
        configurePublishingExtension()
        configureBintrayExtension()
    }

    private
    fun Project.applyPublishingPlugin() {
        apply<MavenPublishPlugin>()
        apply<BintrayPlugin>()
    }

    private
    fun Project.configurePublishingExtension() {
        val sourcesJar: Jar by tasks
        val groovydocJar: Jar by tasks
        val javadocJar: Jar by tasks

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifact(sourcesJar)
                    artifact(groovydocJar)
                    artifact(javadocJar)

                    pom {
                        name.set("Gradle Docker plugin")
                        description.set("Gradle plugin for managing Docker images and containers.")
                        url.set("https://github.com/bmuschko/gradle-docker-plugin")
                        inceptionYear.set("2014")

                        scm {
                            url.set("https://github.com/bmuschko/gradle-docker-plugin")
                            connection.set("scm:https://bmuschko@github.com/bmuschko/gradle-docker-plugin.git")
                            developerConnection.set("scm:git://github.com/bmuschko/gradle-docker-plugin.git")
                        }

                        licenses {
                            license {
                                name.set("The Apache Software License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }

                        developers {
                            developer {
                                id.set("bmuschko")
                                name.set("Benjamin Muschko")
                                url.set("https://github.com/bmuschko")
                            }
                            developer {
                                id.set("cdancy")
                                name.set("Christopher Dancy")
                                url.set("https://github.com/cdancy")
                            }
                            developer {
                                id.set("orzeh")
                                name.set("Łukasz Warchał")
                                url.set("https://github.com/orzeh")
                            }
                        }
                    }
                }
            }
        }
    }

    private
    fun Project.configureBintrayExtension() {
        val packageName = "com.bmuschko:gradle-docker-plugin"

        configure<BintrayExtension> {
            user = resolveProperty("BINTRAY_USER", "bintrayUser")
            key = resolveProperty("BINTRAY_KEY", "bintrayKey")
            setPublications("mavenJava")
            publish = true

            pkg(closureOf<BintrayExtension.PackageConfig> {
                repo = "gradle-plugins"
                name = packageName
                desc = "Gradle plugin for managing Docker images and containers."
                websiteUrl = "https://github.com/bmuschko/${project.name}"
                issueTrackerUrl = "https://github.com/bmuschko/${project.name}/issues"
                vcsUrl = "https://github.com/bmuschko/${project.name}.git"
                setLicenses("Apache-2.0")
                setLabels("gradle", "docker", "container", "image", "lightweight", "vm", "linux")
                publicDownloadNumbers = true
                githubRepo = "bmuschko/${project.name}"

                version(closureOf<BintrayExtension.VersionConfig> {
                    released = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").format(Date())
                    vcsTag = "v${project.version}"
                    setAttributes(mapOf("gradle-plugin" to listOf("com.bmuschko.docker-remote-api:${packageName}",
                            "com.bmuschko.docker-java-application:${packageName}",
                            "com.bmuschko.docker-spring-boot-application:${packageName}")))

                    gpg(closureOf<BintrayExtension.GpgConfig> {
                        sign = true
                        passphrase = resolveProperty("GPG_PASSPHRASE", "gpgPassphrase")
                    })
                    mavenCentralSync(closureOf<BintrayExtension.MavenCentralSyncConfig> {
                        sync = true
                        user = resolveProperty("MAVEN_CENTRAL_USER_TOKEN", "mavenCentralUserToken")
                        password = resolveProperty("MAVEN_CENTRAL_PASSWORD", "mavenCentralPassword")
                        close = "1"
                    })
                })
            })
        }
    }

    private
    fun Project.resolveProperty(envVarKey: String, projectPropKey: String): String? {
        val propValue = System.getenv()[envVarKey]

        if(propValue != null) {
            return propValue
        }

        return findProperty(projectPropKey).toString()
    }
}