plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    // TODO: decouple from backend.common
    compile(project(":compiler:ir.backend.common"))
    compile(project(":compiler:ir.tree"))
    // In fact we don't really need this as a dependency.
    // All klib writer and ir serializer have in common is just
    // SerializedIr and SerializedMetadata.
    // So probably this dependency can be eliminated.
    compile(project(":kotlin-util-io"))
    compile(project(":kotlin-util-klib"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

