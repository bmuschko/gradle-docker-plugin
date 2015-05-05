package com.bmuschko.gradle.docker

import spock.lang.Specification

class DockerJavaApplicationPluginTest extends Specification {
  def "Can convert simple tag into an image name for pushing to DockerHub"() {
    when:
    String name = DockerJavaApplicationPlugin.imageNameFromTag('simple_tag')
    then:
    name == 'simple_tag'
  }

  def "Can convert versioned tag into an image name for pushing to DockerHub"() {
    when:
    String name = DockerJavaApplicationPlugin.imageNameFromTag('versioned_tag:1.0')
    then:
    name == 'versioned_tag'
  }

  def "Can convert simple tag into an image name for pushing to private registry"() {
    when:
    String name = DockerJavaApplicationPlugin.imageNameFromTag('localhost:5000/simple_tag')
    then:
    name == 'localhost:5000/simple_tag'
  }

  def "Can convert versioned tag into an image name for pushing to private registry"() {
    when:
    String name = DockerJavaApplicationPlugin.imageNameFromTag('localhost:5000/versioned_tag:1.0')
    then:
    name == 'localhost:5000/versioned_tag'
  }


}
