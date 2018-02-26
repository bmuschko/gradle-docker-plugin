package com.bmuschko.gradle.docker.utils

import java.lang.reflect.Array

final class CollectionUtil {
    private CollectionUtil() { }

    static Object[] toArray(List list) {
        if (list == null) {
            return new Object[0]
        }

        Class clazz = list[0].getClass()
        list.toArray(Array.newInstance(clazz, 0))
    }
}
