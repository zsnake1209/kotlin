
plugins {
    kotlin("jvm")
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(project(":kotlin-scripting-jvm"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
