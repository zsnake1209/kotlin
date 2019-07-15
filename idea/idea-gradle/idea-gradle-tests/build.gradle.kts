plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    testRuntime(intellijDep())

    Platform[192].orHigher {
        testCompileOnly(intellijPluginDep("java"))
        testRuntimeOnly(intellijPluginDep("java"))
    }

    testCompile(projectTests(":idea"))
    testCompile(projectTests(":idea:idea-test-framework"))

    testCompile(intellijPluginDep("gradle"))
    testCompileOnly(intellijPluginDep("Groovy"))
    testCompileOnly(intellijDep())

    testCompile(project(":idea:idea-native")) { isTransitive = false }
    testCompile(project(":idea:idea-gradle-native")) { isTransitive = false }
    testRuntime(project(":kotlin-native:kotlin-native-library-reader")) { isTransitive = false }
    testRuntime(project(":kotlin-native:kotlin-native-utils")) { isTransitive = false }

    testRuntime(project(":kotlin-reflect"))
    testRuntime(project(":idea:idea-jvm"))
    testRuntime(project(":idea:idea-android"))
    testRuntime(project(":plugins:kapt3-idea"))
    testRuntime(project(":plugins:android-extensions-ide"))
    testRuntime(project(":plugins:lint"))
    testRuntime(project(":sam-with-receiver-ide-plugin"))
    testRuntime(project(":allopen-ide-plugin"))
    testRuntime(project(":noarg-ide-plugin"))
    testRuntime(project(":kotlin-scripting-idea"))
    testRuntime(project(":kotlinx-serialization-ide-plugin"))
    // TODO: the order of the plugins matters here, consider avoiding order-dependency
    testRuntime(intellijPluginDep("junit"))
    testRuntime(intellijPluginDep("testng"))
    testRuntime(intellijPluginDep("properties"))
    testRuntime(intellijPluginDep("gradle"))
    testRuntime(intellijPluginDep("Groovy"))
    testRuntime(intellijPluginDep("coverage"))
    if (Ide.IJ()) {
        testRuntime(intellijPluginDep("maven"))
    }
    testRuntime(intellijPluginDep("android"))
    testRuntime(intellijPluginDep("smali"))
}

sourceSets {
    "test" { projectDefault() }
}

testsJar()

projectTest(parallel = true) {
    workingDir = rootDir
    useAndroidSdk()

    doFirst {
        val testRuntimeSourceSet = javaPluginConvention().sourceSets["test"]
        testRuntimeSourceSet.runtimeClasspath = testRuntimeSourceSet.runtimeClasspath.filter {
            !it.path.contains("${project.buildDir}/resources/main")
        }
    }
}

configureFormInstrumentation()
