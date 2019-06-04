/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.junit.internal.MethodSorter;
import org.junit.internal.runners.JUnit38ClassRunner;

import java.lang.reflect.Method;

import static org.jetbrains.kotlin.test.JUnit3RunnerWithInners.isTestMethod;

/**
 * This class suppress "No tests found in class" warning when inner test classes are present.
 */
class JUnit3RunnerWithInnersForGradle extends JUnit38ClassRunner {

    public JUnit3RunnerWithInnersForGradle(Class<?> klass) {
        super(getTestClass(klass));
    }

    private static Test getTestClass(Class<?> klass) {
        if (!hasOwnTestMethods(klass)) {
            for (Class<?> declaredClass : klass.getDeclaredClasses()) {
                if (TestCase.class.isAssignableFrom(declaredClass)) {
                    return new JUnit3RunnerWithInners.FakeEmptyClassTest(klass);
                }
            }
        }
        return new TestSuite(klass.asSubclass(TestCase.class));
    }

    private static boolean hasOwnTestMethods(Class klass) {
        for (Method each : MethodSorter.getDeclaredMethods(klass)) {
            if (isTestMethod(each)) return true;
        }

        return false;
    }
}