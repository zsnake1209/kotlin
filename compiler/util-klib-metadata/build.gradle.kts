plugins {
    kotlin("jvm")
    id("jps-compatible")
}

description = "Common klib metadata reader and writer"

dependencies {
    compile(kotlinStdlib())
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":core:deserialization"))
    compileOnly(project(":compiler:serialization"))
    compile(project(":kotlin-util-io"))
    compile(project(":kotlin-util-klib"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

publish()

standardPublicJars()
