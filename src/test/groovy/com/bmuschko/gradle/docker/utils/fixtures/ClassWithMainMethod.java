package com.bmuschko.gradle.docker.utils.fixtures;

public class ClassWithMainMethod {
    public void run() {
        System.out.println("Hello World");
    }

    public static void main(String[] args) {
        new ClassWithMainMethod().run();
    }
}
