/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import java.net.URI

// https://youtrack.jetbrains.com/issue/ADM-23180
val mirroredUrls = listOf(
    "https://dl.bintray.com/groovy/maven",
    "https://dl.bintray.com/kotlin/kotlin-dev",
    "https://dl.bintray.com/kotlin/kotlin-eap",
    "https://dl.google.com/dl/android/maven2",
    "https://dl.google.com/go",
    "https://download.jetbrains.com",
    "https://jcenter.bintray.com",
    "https://jetbrains.bintray.com/dekaf",
    "https://jetbrains.bintray.com/intellij-jdk",
    "https://jetbrains.bintray.com/intellij-plugin-service",
    "https://jetbrains.bintray.com/intellij-third-party-dependencies",
    "https://jetbrains.bintray.com/markdown",
    "https://jetbrains.bintray.com/teamcity-rest-client",
    "https://jetbrains.bintray.com/test-discovery",
    "https://jetbrains.bintray.com/jediterm",
    "https://jitpack.io",
    "https://maven.exasol.com/artifactory/exasol-releases",
    "https://plugins.gradle.org/m2",
    "https://plugins.jetbrains.com/maven",
    "https://repo.grails.org/grails/core",
    "https://repo.jenkins-ci.org/releases",
    "https://repo.spring.io/milestone",
    "https://repo1.maven.org/maven2",
    "https://services.gradle.org",
    "https://www.jetbrains.com/intellij-repository",
    "https://www.myget.org/F/intellij-go-snapshots/maven",
    "https://www.myget.org/F/rd-snapshots/maven",
    "https://www.myget.org/F/rd-model-snapshots/maven",
    "https://www.python.org/ftp",
    "https://dl.google.com/dl/android/studio/ide-zips",
    "https://dl.bintray.com/kotlin/ktor",
    "https://cdn.azul.com/zulu/bin"
)

val isTeamcityBuild = project.hasProperty("teamcity") || System.getenv("TEAMCITY_VERSION") != null

fun Project.cacheRedirectorEnabled(): Boolean = findProperty("cacheRedirectorEnabled")?.toString()?.toBoolean() == true

fun URI.toCacheRedirectorUri() = URI("https://cache-redirector.jetbrains.com/$host/$path")

fun URI.maybeRedirect(): URI {
    return if (mirroredUrls.any { toString().trimEnd('/').startsWith(it) }) {
        toCacheRedirectorUri()
    } else {
        this
    }
}

fun RepositoryHandler.redirect() {
    for (repository in this) {
        if (repository is MavenArtifactRepository)
            repository.url = repository.url.maybeRedirect()
        else if (repository is IvyArtifactRepository) {
            if (repository.url != null) {
                repository.url = repository.url.maybeRedirect()
            }
        }
    }
}

// teamcity.jetbrains.com is located in the same local network with build agents
fun URI.isProxiedOrLocal() = host == "cache-redirector.jetbrains.com" || scheme == "file" || host == "teamcity.jetbrains.com"

fun RepositoryHandler.findNonProxiedRepositories(): List<String> {
    val mavenNonProxiedRepos = filterIsInstance<MavenArtifactRepository>()
        .filterNot { it.url.isProxiedOrLocal() }
        .map { it.url.toString() }

    val ivyNonProxiedRepos = filterIsInstance<IvyArtifactRepository>()
        .filterNot { it.url.isProxiedOrLocal() }
        .map { it.url.toString() }

    return mavenNonProxiedRepos + ivyNonProxiedRepos
}

fun escape(s: String): String {
    return s.replace("[\\|'\\[\\]]".toRegex(), "\\|$0").replace("\n".toRegex(), "|n").replace("\r".toRegex(), "|r")
}

fun testFailed(name: String, message: String, details: String) {
    println("##teamcity[testFailed name='%s' message='%s' details='%s']".format(escape(name), escape(message), escape(details)))
}

fun Task.logNonProxiedRepo(project: Project, repoUrl: String) {
    val msg = "Repository $repoUrl in project ${project.displayName} should be proxied with cache-redirector"

    if (isTeamcityBuild) {
        testFailed("Check repositories for: '${project.displayName}'", msg, "")
    }

    logger.warn("WARNING - $msg")
}

fun Task.logInvalidRepo(project: Project) {
    val msg = "Invalid ivy repo found in project $path: Url must be not null"

    if (isTeamcityBuild) {
        testFailed("Check repositories for: '${project.displayName}'", msg, "")
    }

    logger.warn("WARNING - $msg")
}

val checkRepositories = tasks.register("checkRepositories") {
    doLast {
        repositories.filterIsInstance<IvyArtifactRepository>().forEach {
            if (it.url == null) {
                logInvalidRepo(project)
            }
        }

        repositories.findNonProxiedRepositories().forEach {
            logNonProxiedRepo(project, it)
        }

        buildscript.repositories.findNonProxiedRepositories().forEach {
            logNonProxiedRepo(project, it)
        }
    }
}

tasks.findByName("checkBuild")?.dependsOn(checkRepositories)

if (cacheRedirectorEnabled()) {
    logger.info("Redirecting repositories for $displayName")
    repositories.redirect()
}