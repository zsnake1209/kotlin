plugins {
    kotlin("jvm")
    id("jps-compatible")
}

JvmProject.configure(project, "1.8")

dependencies {
    compileOnly(project(":idea"))

    compileOnly(intellijDep())
    compileOnly(intellijPluginDep("git4idea"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {  }
}
