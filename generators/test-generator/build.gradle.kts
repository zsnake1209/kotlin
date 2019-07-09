
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

JvmProject.configure(project, "1.8")

dependencies {
    testCompile(intellijDep()) { includeJars("util") }
    testCompile(project(":core:util.runtime"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(kotlinStdlib())
    testCompile(commonDep("junit:junit"))
}

sourceSets {
    "main" { }
    "test" { projectDefault() }
}

testsJar {}
