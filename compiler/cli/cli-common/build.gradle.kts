
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":core:util.runtime"))
    compile(project(":compiler:frontend"))
    compile(project(":compiler:frontend.java"))
    compile(project(":js:js.frontend"))
    compile(project(":kotlin-util-klib"))
    compile(project(":kotlin-util-klib-metadata"))
    compile(project(":compiler:ir.serialization.common"))
    compileOnly(project(":kotlin-reflect-api"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    compileOnly(intellijDep()) { includeIntellijCoreJarDependencies(project) }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
