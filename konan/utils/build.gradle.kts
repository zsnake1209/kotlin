plugins {
    kotlin("jvm")
    id("jps-compatible")
}

description = "Kotlin/Native utils"

JvmProject.configure(project, "1.8")

dependencies {
    compile(kotlinStdlib())
    compile(project(":kotlin-util-io"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

publish()

standardPublicJars()

