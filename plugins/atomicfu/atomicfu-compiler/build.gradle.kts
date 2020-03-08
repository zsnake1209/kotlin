description = "Atomicfu Compiler Plugin"

plugins {
    kotlin("jvm")
}

dependencies {
    compileOnly(intellijCoreDep()) { includeJars("intellij-core", "asm-all", rootProject = rootProject) }

    compileOnly(project(":compiler:plugin-api"))
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":compiler:backend"))
    compileOnly(project(":compiler:ir.backend.common"))
    compileOnly(project(":js:js.frontend"))
    compileOnly(project(":js:js.translator"))
    compile(project(":compiler:backend.js"))

    runtime(kotlinStdlib())

    testCompile(projectTests(":compiler:tests-common"))
    testCompile(projectTests(":js:js.tests"))
    testCompile(commonDep("junit:junit"))

    testRuntime(kotlinStdlib())
    testRuntime(project(":kotlin-reflect"))
    testRuntime(project(":kotlin-preloader")) // it's required for ant tests
    testRuntime(project(":compiler:backend-common"))
    testRuntime(commonDep("org.fusesource.jansi", "jansi"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true) {
    workingDir = rootDir
}

apply(from = "$rootDir/gradle/kotlinPluginPublication.gradle.kts")

fun Test.setUpJsBoxTests(jsEnabled: Boolean, jsIrEnabled: Boolean) {
    dependsOn(":dist")
    if (jsIrEnabled) {
        dependsOn(":kotlin-stdlib-js-ir:generateFullRuntimeKLib")
        dependsOn(":kotlin-stdlib-js-ir:generateReducedRuntimeKLib")
        dependsOn(":kotlin-stdlib-js-ir:generateKotlinTestKLib")
    }
}

val generateTests by generator("org.jetbrains.kotlin.generators.tests.GenerateJsTestsKt")
val testDataDir = project(":js:js.translator").projectDir.resolve("testData")

