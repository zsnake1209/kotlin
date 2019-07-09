
description = "Kotlin Ant Tools"

plugins {
    kotlin("jvm")
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(commonDep("org.apache.ant", "ant"))
    compile(project(":kotlin-preloader"))
    compile(kotlinStdlib())
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

runtimeJar {
    manifest.attributes["Class-Path"] = "$compilerManifestClassPath kotlin-preloader.jar"
}
