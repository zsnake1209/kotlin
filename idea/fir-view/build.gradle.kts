/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

plugins {
    kotlin("jvm")
    `jps-compatible`
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(kotlinStdlib())
    compileOnly(project(":kotlin-reflect-api"))
    compile(project(":core:descriptors"))
    compile(project(":compiler:fir:tree"))
    compile(project(":compiler:fir:psi2fir"))

    compile(project(":idea:idea-core"))

    compileOnly(intellijDep())

    Platform[192].orHigher {
        compileOnly(intellijPluginDep("java"))
    }

    compileOnly(intellijPluginDep("gradle"))
}

sourceSets {
    "main" {
        projectDefault()
    }
    "test" {}
}
