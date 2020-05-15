plugins {
    java
}

sourceSets {
    "main" { projectDefault() }
    "test" { }
}

publish()
sourcesJar {
    into("commonMain") {
        from(projectDir.resolve("../compose/runtime/src/commonMain"))
    }

    into("jsMain") {
        from(projectDir.resolve("../compose/runtime/src/jsMain"))
    }
}