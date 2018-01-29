
plugins { kotlin("jvm") }

dependencies {
    val compile by configurations
    compile(projectDist(":kotlin-stdlib"))
    compile(projectDist(":kotlin-script-runtime"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { }
}

projectTest {
    workingDir = rootDir
}
