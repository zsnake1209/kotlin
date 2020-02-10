plugins {
    kotlin("jvm")
}

repositories {
    maven("https://kotlin.bintray.com/dukat")
}

dependencies {
    implementation(kotlinStdlib())
    implementation("org.jetbrains.dukat:dukat:0.0.27")
    implementation("org.jsoup:jsoup:1.12.1")
}

task("downloadIDL", JavaExec::class) {
    main = "org.jetbrains.kotlin.tools.dukat.DownloadKt"
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn(":dukat:build")
}

task("generateStdlibFromIDL", JavaExec::class) {
    main = "org.jetbrains.kotlin.tools.dukat.LaunchKt"
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn(":dukat:build")
    systemProperty("line.separator", "\n")
}
