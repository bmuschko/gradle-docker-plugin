package com.bmuschko.gradle.docker.internal;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.*;
import java.util.*;

/**
 * Original source from Spring Boot's loader tools licensed under Apache License Version 2.0.
 */
public class MainClassFinder {
    private static final String DOT_CLASS = ".class";
    private static final String MAIN_METHOD_NAME = "main";
    private static final Type STRING_ARRAY_TYPE = Type.getType(String[].class);
    private static final Type MAIN_METHOD_TYPE = Type.getMethodType(Type.VOID_TYPE, STRING_ARRAY_TYPE);

    public static String findSingleMainClass(File rootFolder) throws IOException {
        return findSingleMainClass(rootFolder, null);
    }

    public static String findSingleMainClass(File rootFolder, String annotationName) throws IOException {
        SingleMainClassCallback callback = new SingleMainClassCallback(annotationName);
        MainClassFinder.doWithMainClasses(rootFolder, callback);
        return callback.getMainClassName();
    }
    public final static class MainClass {
        private final String name;
        private final Set<String> annotationNames;

        public MainClass(String name, Set<String> annotationNames) {
            this.name = name;
            this.annotationNames = Collections.unmodifiableSet(annotationNames);
        }

        public String getName() {
            return this.name;
        }

        public Set<String> getAnnotationNames() {
            return this.annotationNames;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!getClass().equals(obj.getClass())) {
                return false;
            }
            MainClass other = (MainClass) obj;
            if (!this.name.equals(other.getName())) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    public interface MainClassCallback<T> {
        T doWith(MainClass mainClass);
    }

    final private static class SingleMainClassCallback implements MainClassCallback<Object> {
        private final Set<MainClass> mainClasses = new LinkedHashSet<MainClass>();
        private final String annotationName;

        private SingleMainClassCallback(String annotationName) {
            this.annotationName = annotationName;
        }

        @Override
        public Object doWith(MainClass mainClass) {
            this.mainClasses.add(mainClass);
            return null;
        }

        private String getMainClassName() {
            Set<MainClass> matchingMainClasses = new LinkedHashSet<MainClass>();
            if (this.annotationName != null) {
                for (MainClass mainClass : this.mainClasses) {
                    if (mainClass.getAnnotationNames().contains(this.annotationName)) {
                        matchingMainClasses.add(mainClass);
                    }
                }
            }
            if (matchingMainClasses.isEmpty()) {
                matchingMainClasses.addAll(this.mainClasses);
            }
            if (matchingMainClasses.size() > 1) {
                throw new IllegalStateException("Unable to find a single main class from the following candidates " + matchingMainClasses);
            }
            return (matchingMainClasses.isEmpty() ? null : matchingMainClasses.iterator().next().getName());
        }

    }

    public static <T> T doWithMainClasses(File rootFolder, MainClassCallback<T> callback)
        throws IOException {
        if (!rootFolder.exists()) {
            return null; // nothing to do
        }
        if (!rootFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid root folder '" + rootFolder + "'");
        }
        String prefix = rootFolder.getAbsolutePath() + "/";
        Deque<File> stack = new ArrayDeque<File>();
        stack.push(rootFolder);
        while (!stack.isEmpty()) {
            File file = stack.pop();
            if (file.isFile()) {
                InputStream inputStream = new FileInputStream(file);
                ClassDescriptor classDescriptor = createClassDescriptor(inputStream);
                if (classDescriptor != null && classDescriptor.isMainMethodFound()) {
                    String className = convertToClassName(file.getAbsolutePath(), prefix);
                    T result = callback.doWith(new MainClass(className, classDescriptor.getAnnotationNames()));
                    if (result != null) {
                        return result;
                    }
                }
            }
            if (file.isDirectory()) {
                pushAllSorted(stack, file.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isDirectory() && !pathname.getName().startsWith(".");
                    }

                }));
                pushAllSorted(stack, file.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isFile() && pathname.getName().endsWith(DOT_CLASS);
                    }

                }));
            }
        }
        return null;
    }

    private static ClassDescriptor createClassDescriptor(InputStream inputStream) {
        try (inputStream) {
            ClassReader classReader = new ClassReader(inputStream);
            ClassDescriptor classDescriptor = new ClassDescriptor();
            classReader.accept(classDescriptor, ClassReader.SKIP_CODE);
            return classDescriptor;
        } catch (IOException ex) {
            return null;
        }
    }

    private static String convertToClassName(String name, String prefix) {
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        name = name.substring(0, name.length() - DOT_CLASS.length());
        if (prefix != null) {
            name = name.substring(prefix.length());
        }
        return name;
    }

    private static void pushAllSorted(Deque<File> stack, File[] files) {
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o2.compareTo(o1);
            }
        });
        for (File file : files) {
            stack.push(file);
        }
    }

    private static class ClassDescriptor extends ClassVisitor {
        private final Set<String> annotationNames = new LinkedHashSet<String>();
        private boolean mainMethodFound;

        public ClassDescriptor() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            this.annotationNames.add(Type.getType(desc).getClassName());
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            if (isAccess(access, Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC) && MAIN_METHOD_NAME.equals(name) && MAIN_METHOD_TYPE.getDescriptor().equals(desc)) {
                this.mainMethodFound = true;
            }
            return null;
        }

        private boolean isAccess(int access, Integer... requiredOpsCodes) {
            for (int requiredOpsCode : requiredOpsCodes) {
                if ((access & requiredOpsCode) == 0) {
                    return false;
                }
            }
            return true;
        }

        public boolean isMainMethodFound() {
            return this.mainMethodFound;
        }

        public Set<String> getAnnotationNames() {
            return this.annotationNames;
        }
    }
}
