description = "JetPack Compose Runtime Gradle Plugin"

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `kotlin-dsl`
}

dependencies {
    runtime(kotlin("stdlib"))

    implementation(kotlin("gradle-plugin"))
}
