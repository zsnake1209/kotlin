
description = "Kotlin \"main\" script definition tests"

plugins {
    kotlin("jvm")
}

JvmProject.configure(project, "1.8")

dependencies {
    testCompile(project(":kotlin-main-kts"))
    testCompile(project(":kotlin-scripting-jvm-host-embeddable"))
    testCompile(commonDep("junit"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

projectTest(parallel = true) {
    dependsOn(":dist")
    workingDir = rootDir
}
