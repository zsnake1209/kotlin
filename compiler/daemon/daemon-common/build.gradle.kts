plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(kotlinStdlib())
    implementation(project(":compiler:util"))
    implementation(project(":compiler:cli-common"))
    implementation(project(":compiler:ic:ic-js-base"))
    implementation(commonDep("org.jetbrains.kotlinx", "kotlinx-coroutines-core")) {
        isTransitive = false
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

