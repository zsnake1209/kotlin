plugins {
    kotlin("jvm")
    id("jps-compatible")
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(project(":compiler:psi"))
    compile(project(":compiler:container"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
}


sourceSets {
    "main" {
        projectDefault()
    }
    "test" {}
}