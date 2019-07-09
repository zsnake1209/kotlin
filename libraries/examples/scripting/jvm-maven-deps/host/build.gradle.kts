
plugins {
    kotlin("jvm")
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(project(":examples:scripting-jvm-maven-deps"))
    compile(project(":kotlin-scripting-jvm-host"))
    compile(kotlinStdlib())
    compileOnly(project(":kotlin-reflect-api"))
    compileOnly(project(":compiler:util"))

    testRuntimeOnly(project(":kotlin-compiler"))
    testRuntimeOnly(project(":kotlin-reflect"))
    testRuntimeOnly(project(":kotlin-scripting-compiler"))

    testCompile(commonDep("junit"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}
