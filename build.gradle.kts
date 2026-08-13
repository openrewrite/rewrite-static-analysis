@file:Suppress("UnstableApiUsage")

import java.io.InputStream
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.ExecOperations
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

val provided = configurations.named("provided")
val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.meta:rewrite-analysis:${rewriteVersion}")
    implementation("org.apache.commons:commons-text:latest.release")

    // Limit transitive dependencies for downstream projects like rewrite-spring
    provided("org.openrewrite:rewrite-groovy:${rewriteVersion}")
    provided("org.openrewrite:rewrite-javascript:${rewriteVersion}")
    provided("org.openrewrite:rewrite-kotlin:${rewriteVersion}")
    provided("org.openrewrite:rewrite-csharp:${rewriteVersion}")
    provided("org.openrewrite:rewrite-python:${rewriteVersion}")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.jetbrains:annotations:24.+")
    testImplementation("org.junit-pioneer:junit-pioneer:2.+")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.testng:testng:7.+")
    testImplementation("org.openrewrite:rewrite-javascript:${rewriteVersion}")

    testImplementation("com.google.code.gson:gson:latest.release")

    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
}

// The TypeScript tests spawn `npx --package=@openrewrite/rewrite@<version> rewrite-rpc`, and the RPC
// process is shut down between test classes. On a cold npx cache each of those starts its own install
// into the same `~/.npm/_npx` directory, and the resulting overlap leaves the package half-written, so
// the tests fail with "RPC process shut down early". Installing once up front keeps every spawn a
// cache hit.
//
// The marker is present once that install succeeded and the tests can spawn the package, and absent
// when it did not happen, whether for a missing npx or a version npm does not have.
val javaScriptRpcMarker = layout.buildDirectory.file("tmp/warmJavaScriptRpcCache/version.txt")

val warmJavaScriptRpcCache by tasks.registering {
    description = "Installs the npm package that the JavaScript RPC tests spawn, so they never race on a cold npx cache."
    val rewriteJavaScriptJars = configurations.named("testRuntimeClasspath")
        .map { classpath -> classpath.filter { it.name.startsWith("rewrite-javascript-") } }
    val marker = javaScriptRpcMarker
    val npx = if (System.getProperty("os.name").lowercase().contains("windows")) "npx.cmd" else "npx"
    val execOperations = serviceOf<ExecOperations>()

    inputs.files(rewriteJavaScriptJars)
    outputs.file(marker)
    // The marker records an install that succeeded. Gradle counts a declared output that was absent
    // last time and is absent now as up to date, so without asking explicitly, an install that failed
    // once is never retried.
    outputs.upToDateWhen { marker.get().asFile.isFile }

    doLast {
        val markerFile = marker.get().asFile
        markerFile.delete()
        val jar = rewriteJavaScriptJars.get().singleOrNull() ?: return@doLast
        val version = zipTree(jar).matching { include("META-INF/rewrite-javascript-version.txt") }
            .singleFile.readText().trim()
        if (version.endsWith("-SNAPSHOT")) {
            // A locally built rewrite-javascript, resolved from mavenLocal, spawns an `npm link`ed
            // rewrite-rpc from its working copy rather than a published package. Nothing to install.
            markerFile.parentFile.mkdirs()
            markerFile.writeText(version)
            return@doLast
        }
        val exitValue = try {
            execOperations.exec {
                commandLine(npx, "--yes", "--package=@openrewrite/rewrite@$version", "rewrite-rpc")
                standardInput = InputStream.nullInputStream()
                isIgnoreExitValue = true
            }.exitValue
        } catch (e: Exception) {
            // isIgnoreExitValue covers a process that exits non-zero, not one that never starts,
            // which is what an absent npx looks like. Running the Java tests should not require Node.
            logger.warn("Could not run $npx (${e.message}); JavaScript RPC tests may be flaky.")
            return@doLast
        }
        if (exitValue != 0) {
            logger.warn("Could not pre-install @openrewrite/rewrite@$version; JavaScript RPC tests may be flaky.")
            return@doLast
        }
        markerFile.parentFile.mkdirs()
        markerFile.writeText(version)
    }
}

tasks.withType<Test> {
    jvmArgs("-Xmx1g", "-Xms512m")
    dependsOn(warmJavaScriptRpcCache)
    // A published rewrite-javascript snapshot pins an exact @openrewrite/rewrite version, and the npm
    // release of that version can lag the Maven one, leaving nothing for the RPC process to run. Tests
    // annotated with @RequiresJavaScriptRpc skip rather than fail the build over that gap upstream.
    val marker = javaScriptRpcMarker
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-DjavaScriptRpcAvailable=${marker.get().asFile.isFile}")
    })
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
