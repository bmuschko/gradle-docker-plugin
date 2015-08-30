package com.bmuschko.gradle.docker.utils

import groovy.transform.EqualsAndHashCode
import spock.lang.Specification

class CollectionUtilTest extends Specification {
    def "can convert null list of Strings into array of Strings"() {
        given:
        List<String> strList = null

        when:
        String[] strArray = CollectionUtil.toArray(strList)

        then:
        !strArray
    }

    def "can convert empty list of Strings into array of Strings"() {
        given:
        List<String> strList = [] as List<String>

        when:
        String[] strArray = CollectionUtil.toArray(strList)

        then:
        strArray instanceof String[]
        strArray.length == 0
    }

    def "can convert list of Strings into array of Strings"() {
        given:
        List<String> strList = ['a', 'b', 'c']

        when:
        String[] strArray = CollectionUtil.toArray(strList)

        then:
        strArray instanceof String[]
        strArray.length == 3
        strArray[0] == 'a'
        strArray[1] == 'b'
        strArray[2] == 'c'
    }

    def "can convert list of POJOs into array of POJOs"() {
        given:
        List<Person> personList = [new Person(firstName: 'Jon', lastName: 'Doe', age: 28),
                                   new Person(firstName: 'Carla', lastName: 'Born', age: 12),
                                   new Person(firstName: 'Frieda', lastName: 'Young', age: 99)]

        when:
        Person[] personArray = CollectionUtil.toArray(personList)

        then:
        personArray instanceof Person[]
        personArray.length == 3
        personArray[0] == new Person(firstName: 'Jon', lastName: 'Doe', age: 28)
        personArray[1] == new Person(firstName: 'Carla', lastName: 'Born', age: 12)
        personArray[2] == new Person(firstName: 'Frieda', lastName: 'Young', age: 99)
    }

    @EqualsAndHashCode
    private class Person {
        String firstName
        String lastName
        Integer age
    }
}
