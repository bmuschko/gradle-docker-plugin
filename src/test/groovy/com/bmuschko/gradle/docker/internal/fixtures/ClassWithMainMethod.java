package com.bmuschko.gradle.docker.internal.fixtures;

public class ClassWithMainMethod {
    public void run() {
        System.out.println("Hello World");
    }

    public static void main(String[] args) {
        new ClassWithMainMethod().run();
    }
}
