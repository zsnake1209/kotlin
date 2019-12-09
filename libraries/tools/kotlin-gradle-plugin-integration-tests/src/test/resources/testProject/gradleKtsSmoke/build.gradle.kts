buildscript {
    val kotlin_version: String by extra
    repositories {
        mavenLocal()
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
    }
}

apply(plugin = "kotlin")

repositories {
    mavenLocal()
    jcenter()
}
