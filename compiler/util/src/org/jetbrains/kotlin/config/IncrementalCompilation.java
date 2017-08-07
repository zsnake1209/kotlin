package org.jetbrains.kotlin.config;

import org.jetbrains.annotations.TestOnly;

public class IncrementalCompilation {
    private static final String INCREMENTAL_COMPILATION_PROPERTY = "kotlin.incremental.compilation";
    private static final String INCREMENTAL_COMPILATION_JS_PROPERTY = "kotlin.incremental.compilation.js";

    public static boolean isEnabled() {
        return "true".equals(System.getProperty(INCREMENTAL_COMPILATION_PROPERTY));
    }

    public static boolean isEnabledForJs() {
        return "true".equals(System.getProperty(INCREMENTAL_COMPILATION_JS_PROPERTY));
    }

    @TestOnly
    public static void setIsEnabled(boolean value) {
        System.setProperty(INCREMENTAL_COMPILATION_PROPERTY, String.valueOf(value));
    }

    @TestOnly
    public static void setIsEnabledForJs(boolean value) {
        System.setProperty(INCREMENTAL_COMPILATION_JS_PROPERTY, String.valueOf(value));
    }
}