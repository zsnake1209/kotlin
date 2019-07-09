plugins {
    kotlin("jvm")
    `jps-compatible`
}

JvmProject.configure(project, "1.8")

dependencies {
    compile(project(":compiler:util"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    compileOnly(intellijDep())
    compileOnly(intellijPluginDep("gradle"))
    compileOnly(intellijPluginDep("android"))
}

sourceSets {
    if (Ide.IJ() && Platform[183].orLower()) {
        "main" {
            projectDefault()
        }
    } else {
        "main" {}
    }
    "test" {}
}

runtimeJar()
