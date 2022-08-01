package com.bmuschko.gradle.docker.internal

import com.bmuschko.gradle.docker.internal.fixtures.AnnotatedClassWithMainMethod
import com.bmuschko.gradle.docker.internal.fixtures.ClassWithMainMethod
import com.bmuschko.gradle.docker.internal.fixtures.ClassWithoutMainMethod
import com.bmuschko.gradle.docker.internal.fixtures.TestJarFile
import spock.lang.Specification
import spock.lang.TempDir

class MainClassFinderTest extends Specification {
    private static final String ANNOTATION_CLASS_NAME = "com.bmuschko.gradle.docker.internal.fixtures.SomeApplication"

    @TempDir
    File temporaryFolder

    TestJarFile testJarFile

    void setup() throws IOException {
        testJarFile = new TestJarFile(temporaryFolder)
    }

    def "can find main class without annotation"() {
        given:
        testJarFile.addClass('a/B.class', ClassWithMainMethod)
        testJarFile.addClass('a/b/c/E.class', ClassWithoutMainMethod)

        when:
        String mainClass = MainClassFinder.findSingleMainClass(testJarFile.getJarSource())

        then:
        mainClass == 'a.B'
    }

    def "can select main class among multiple"() {
        given:
        testJarFile.addClass('a/B.class', ClassWithMainMethod)
        testJarFile.addClass('a/C.class', ClassWithMainMethod)
        testJarFile.addClass('a/D.class', ClassWithMainMethod)

        when:
        MainClassFinder.findSingleMainClass(testJarFile.getJarSource())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Unable to find a single main class from the following candidates [a.B, a.C, a.D]'
    }

    def "can find annotated main class"() {
        given:
        testJarFile.addClass('a/B.class', ClassWithMainMethod)
        testJarFile.addClass('a/b/c/E.class', AnnotatedClassWithMainMethod)

        when:
        String mainClass = MainClassFinder.findSingleMainClass(testJarFile.getJarSource(), ANNOTATION_CLASS_NAME)

        then:
        mainClass == 'a.b.c.E'
    }

    def "throws exception if annotated main class cannot be found"() {
        given:
        testJarFile.addClass('a/B.class', ClassWithMainMethod)
        testJarFile.addClass('a/b/c/E.class', ClassWithMainMethod)

        when:
        MainClassFinder.findSingleMainClass(testJarFile.getJarSource(), ANNOTATION_CLASS_NAME)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Unable to find a single main class from the following candidates [a.B, a.b.c.E]'
    }

    def "returns null if no main class can be found"() {
        given:
        testJarFile.addClass('a/B.class', ClassWithoutMainMethod)
        testJarFile.addClass('a/b/c/E.class', ClassWithoutMainMethod)

        when:
        String mainClass = MainClassFinder.findSingleMainClass(testJarFile.getJarSource(), ANNOTATION_CLASS_NAME)

        then:
        !mainClass
    }

    def "only consider .class files"() {
        given:
        String textFileName = 'noClass.txt'
        File textFile = new File(temporaryFolder, textFileName)
        textFile << "Hello World!"
        testJarFile.addClass('a/b/c/E.class', AnnotatedClassWithMainMethod)
        testJarFile.addFile(textFileName, textFile)

        when:
        String mainClass = MainClassFinder.findSingleMainClass(testJarFile.getJarSource(), ANNOTATION_CLASS_NAME)

        then:
        mainClass == 'a.b.c.E'
    }
}
