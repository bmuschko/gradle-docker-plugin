package com.bmuschko.gradle.docker.internal.fixtures;

@SomeApplication
public class AnnotatedClassWithMainMethod {
    public void run() {
        System.out.println("Hello World");
    }

    public static void main(String[] args) {
        new AnnotatedClassWithMainMethod().run();
    }
}
