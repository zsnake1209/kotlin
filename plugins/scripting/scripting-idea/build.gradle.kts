
description = "Kotlin Scripting IDEA Plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

JvmProject.configure(project, "1.8")

dependencies {
    compileOnly(project(":kotlin-scripting-compiler"))
    compile(project(":kotlin-scripting-compiler-impl"))
    compile(project(":kotlin-scripting-intellij"))
    compileOnly(project(":idea:idea-gradle"))
    compileOnly(project(":idea:idea-core"))
    compileOnly(intellijDep())
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

runtimeJar()

