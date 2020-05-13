description = "JetPack Compose Gradle plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    // TODO: Check for unnecessary deps

    compileOnly(project(":kotlin-gradle-plugin"))
    compileOnly(project(":kotlin-gradle-plugin-api"))

    runtime(kotlinStdlib())

    compileOnly(project(":kotlin-compiler-embeddable", "runtimeJar"))

    embedded(project(":compose-compiler-plugin")) { isTransitive = false }
}

sourceSets {
    "main" { projectDefault() }
}

publish()

runtimeJar(rewriteDefaultJarDepsToShadedCompiler())

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest {
    workingDir = rootDir
}

pluginBundle {
    plugins {
        create("composePlugin") {
            id = "org.jetbrains.compose.plugin"
            description = "Kotlin compiler plugin for Compose"
            displayName = "Kotlin compiler plugin for Compose"
        }
    }
}
