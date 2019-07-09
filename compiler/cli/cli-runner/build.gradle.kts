
description = "Kotlin Runner"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(kotlinStdlib())
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

runtimeJar {
    manifest.attributes["Main-Class"] = "org.jetbrains.kotlin.runner.Main"
    manifest.attributes["Class-Path"] = "kotlin-stdlib.jar"
}
