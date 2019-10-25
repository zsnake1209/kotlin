import org.jetbrains.kotlin.gradle.targets.js.testing.chromium.chromium

plugins {
    kotlin("js") version "<pluginMarkerVersion>"
}

group = "com.example"
version = "1.0"

repositories {
    mavenLocal()
    jcenter()
}

kotlin {
    target {
        browser()
    }

    sourceSets {
        getByName("main") {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }

        getByName("test") {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

chromium

tasks {
    val chromiumFolderRemove by registering {
        doLast {
            chromium.installationDir.deleteRecursively()
        }
    }

    val chromiumFolderCheck by registering {
        dependsOn(getByName("kotlinChromiumSetup"))

        doLast {
            if (!chromium.installationDir.exists()) {
                throw GradleException()
            }
        }
    }

    val chromiumConcreteVersionFolderChecker by registering {
        dependsOn(getByName("kotlinChromiumSetup"))

        doLast {
            if (!chromium.installationDir.resolve("674921").exists()) {
                throw GradleException()
            }
        }
    }
}