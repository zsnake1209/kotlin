import com.moowork.gradle.node.npm.NpmTask
import com.moowork.gradle.node.task.NodeTask

plugins {
    kotlin("jvm")
    id("com.github.node-gradle.node")
}

// A simple CLI for creating JS IR klibs.
// Does not depend on backend lowerings and JS codegen.
val jsIrKlibCli: Configuration by configurations.creating

// Full JS IR compiler CLI
val fullJsIrCli: Configuration by configurations.creating

dependencies {
    jsIrKlibCli(project(":compiler:cli-js-klib"))

    fullJsIrCli(project(":compiler:cli-js"))
    fullJsIrCli(project(":compiler:util"))
    fullJsIrCli(project(":compiler:cli-common"))
    fullJsIrCli(project(":compiler:cli"))
    fullJsIrCli(project(":compiler:frontend"))
    fullJsIrCli(project(":compiler:backend-common"))
    fullJsIrCli(project(":compiler:backend"))
    fullJsIrCli(project(":compiler:ir.backend.common"))
    fullJsIrCli(project(":compiler:ir.serialization.js"))
    fullJsIrCli(project(":compiler:backend.js"))
    fullJsIrCli(project(":js:js.translator"))
    fullJsIrCli(project(":js:js.serializer"))
    fullJsIrCli(project(":js:js.dce"))
    fullJsIrCli(kotlin("reflect"))
    fullJsIrCli(intellijCoreDep()) { includeJars("intellij-core") }
    fullJsIrCli(intellijDep()) {
        includeJars("picocontainer", "trove4j", "guava", "jdom", rootProject = rootProject)
    }
}

val unimplementedNativeBuiltIns =
  (file("$rootDir/core/builtins/native/kotlin/").list().toSet() - file("$rootDir/libraries/stdlib/js-ir/builtins/").list())
    .map { "core/builtins/native/kotlin/$it" }

// Required to compile native builtins with the rest of runtime
val builtInsHeader = """@file:Suppress(
    "NON_ABSTRACT_FUNCTION_WITH_NO_BODY",
    "MUST_BE_INITIALIZED_OR_BE_ABSTRACT",
    "EXTERNAL_TYPE_EXTENDS_NON_EXTERNAL_TYPE",
    "PRIMARY_CONSTRUCTOR_DELEGATION_CALL_EXPECTED",
    "WRONG_MODIFIER_TARGET"
)
"""

val fullRuntimeSources by task<Sync> {

    val sources = listOf(
        "core/builtins/src/kotlin/",
        "libraries/stdlib/common/src/",
        "libraries/stdlib/src/kotlin/",
        "libraries/stdlib/unsigned/",
        "libraries/stdlib/js/src/",
        "libraries/stdlib/js/runtime/",
        "libraries/stdlib/js-ir/builtins/",
        "libraries/stdlib/js-ir/src/",
        "libraries/stdlib/js-ir/runtime/",

        // TODO get rid - move to test module
        "js/js.translator/testData/_commonFiles/"
    ) + unimplementedNativeBuiltIns

    val excluded = listOf(
        // stdlib/js/src/generated is used exclusively for current `js-v1` backend.
        "libraries/stdlib/js/src/generated/**",

        // JS-specific optimized version of emptyArray() already defined
        "core/builtins/src/kotlin/ArrayIntrinsics.kt"
    )

    sources.forEach { path ->
        from("$rootDir/$path") {
            into(path.dropLastWhile { it != '/' })
            excluded.filter { it.startsWith(path) }.forEach {
                exclude(it.substring(path.length))
            }
        }
    }

    into("$buildDir/fullRuntime/src")

    doLast {
        unimplementedNativeBuiltIns.forEach { path ->
            val file = File("$buildDir/fullRuntime/src/$path")
            val sourceCode = builtInsHeader + file.readText()
            file.writeText(sourceCode)
        }
    }
}

fun JavaExec.buildKLib(sources: List<String>, dependencies: List<String>, outPath: String, commonSources: List<String>) {
    inputs.files(sources)
    outputs.dir(file(outPath).parent)
    classpath = jsIrKlibCli
    main = "org.jetbrains.kotlin.ir.backend.js.GenerateJsIrKlibKt"
    workingDir = rootDir
    args = sources.toList() + listOf("-o", outPath) + dependencies.flatMap { listOf("-d", it) } + commonSources.flatMap { listOf("-c", it) }

    dependsOn(":compiler:cli-js-klib:jar")
    passClasspathInJar()
}

val fullRuntimeDir = buildDir.resolve("fullRuntime/klib")

val generateFullRuntimeKLib by eagerTask<NoDebugJavaExec> {
    dependsOn(fullRuntimeSources)

    buildKLib(sources = listOf(fullRuntimeSources.get().outputs.files.singleFile.path),
              dependencies = emptyList(),
              outPath = fullRuntimeDir.absolutePath,
              commonSources = listOf("common", "src", "unsigned").map { "$buildDir/fullRuntime/src/libraries/stdlib/$it" }
    )
}

val packFullRuntimeKLib by tasks.registering(Jar::class) {
    dependsOn(generateFullRuntimeKLib)
    from(fullRuntimeDir)
    destinationDirectory.set(rootProject.buildDir.resolve("js-ir-runtime"))
    archiveFileName.set("full-runtime.klib")
}

val generateWasmRuntimeKLib by eagerTask<NoDebugJavaExec> {
    buildKLib(sources = listOf("$rootDir/libraries/stdlib/wasm"),
              dependencies = emptyList(),
              outPath = "$buildDir/wasmRuntime/klib",
              commonSources = emptyList()
    )
}

val kotlinTestCommonSources = listOf(
    "$rootDir/libraries/kotlin.test/annotations-common/src/main",
    "$rootDir/libraries/kotlin.test/common/src/main"
)
val generateKotlinTestKLib by eagerTask<NoDebugJavaExec> {
    dependsOn(generateFullRuntimeKLib)

    buildKLib(
        sources = listOf("$rootDir/libraries/kotlin.test/js/src/main") + kotlinTestCommonSources,
        dependencies = listOf("${generateFullRuntimeKLib.outputs.files.singleFile.path}/klib"),
        outPath = "$buildDir/kotlin.test/klib",
        commonSources = kotlinTestCommonSources
    )
}

val jsTestDir = "${buildDir}/testSrc"

val prepareStdlibTestSources by task<Sync> {
    from("$rootDir/libraries/stdlib/test") {
        exclude("src/generated/**")
        into("test")
    }
    from("$rootDir/libraries/stdlib/common/test") {
        exclude("src/generated/**")
        into("common")
    }
    from("$rootDir/libraries/stdlib/js/test") {
        into("js")
    }
    into(jsTestDir)
}

fun JavaExec.buildJs(sources: List<String>, dependencies: List<String>, outPath: String, commonSources: List<String>) {
    inputs.files(sources)
    outputs.dir(file(outPath).parent)
    classpath = fullJsIrCli
    main = "org.jetbrains.kotlin.cli.js.K2JsIrCompiler"
    workingDir = rootDir

    val libraryString: String = dependencies.joinToString(File.pathSeparator)
    val libraryArgs: List<String> = if (libraryString.isEmpty()) emptyList() else listOf<String>("-libraries", libraryString, "-Xfriend-modules=$libraryString")
    val allArgs =
     sources.toList() + listOf("-output", outPath) + libraryArgs + listOf(
         "-Xir-produce-js",
         "-Xmulti-platform",
         "-Xuse-experimental=kotlin.Experimental",
         "-Xuse-experimental=kotlin.contracts.ExperimentalContracts",
         "-Xuse-experimental=kotlin.ExperimentalMultiplatform",
         "-Xuse-experimental=kotlin.ExperimentalStdlibApi",
         "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
     )
    args = allArgs

    dependsOn(":compiler:cli-js:jar")
    passClasspathInJar()
}

val testOutputFile = "$buildDir/kotlin-stdlib-js-ir_test.js"

val tryRunFullCli by eagerTask<NoDebugJavaExec> {
    dependsOn(prepareStdlibTestSources)
    dependsOn(generateFullRuntimeKLib)
    dependsOn(generateKotlinTestKLib)

    buildJs(
        sources = listOf(jsTestDir),
        dependencies = listOf(
            "${generateFullRuntimeKLib.outputs.files.singleFile.path}/klib",
            "${generateKotlinTestKLib.outputs.files.singleFile.path}/klib"
        ),
        outPath = testOutputFile,
        commonSources = emptyList()
    )
}

node {
    download = true
    version = "10.16.2"
    nodeModulesDir = buildDir
}

val installMocha by task<NpmTask> {
    setArgs(listOf("install", "mocha"))
}

val installTeamcityReporter by task<NpmTask> {
    setArgs(listOf("install", "mocha-teamcity-reporter"))
}

// TODO: TEST OUTPUT FILE
// val kotlinTestTestOutputFile = "${project(':kotlin-test:kotlin-test-js').buildDir}/classes/kotlin/test/kotlin-test-js-ir_test.js"

val runMocha by task<NodeTask> {
    dependsOn(installMocha)
    dependsOn(tryRunFullCli)

    script = file("${buildDir}/node_modules/mocha/bin/mocha")

    if (project.hasProperty("teamcity")) {
        dependsOn(installTeamcityReporter)
        setArgs(
            listOf(
                "--reporter",
                "mocha-teamcity-reporter",
                "--reporter-options",
                "topLevelSuite=stdlib-js-ir"
            )
        )
    }
    else {
        setArgs(listOf("--reporter", "min"))
    }

    val allArgs = args.toList() + listOf(testOutputFile)
    setArgs(allArgs)

    setIgnoreExitValue(rootProject.getBooleanProperty("ignoreTestFailures") ?: false)
    setWorkingDir(buildDir)
}

tasks {
    test {
        dependsOn(runMocha)
    }
}