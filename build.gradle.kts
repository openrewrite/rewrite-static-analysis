@file:Suppress("UnstableApiUsage")

import java.io.InputStream
import org.gradle.process.ExecOperations
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

recipeDependencies {
    testParserClasspath("com.google.code.gson:gson:2.+")
    testParserClasspath("junit:junit:4.13.2")
    testParserClasspath("org.testng:testng:7.+")
}

val provided = configurations.named("provided")
val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.apache.commons:commons-text:latest.release")

    // Limit transitive dependencies for downstream projects like rewrite-spring
    provided("org.openrewrite:rewrite-groovy:${rewriteVersion}")
    provided("org.openrewrite:rewrite-javascript:${rewriteVersion}")
    provided("org.openrewrite:rewrite-kotlin:${rewriteVersion}")
    provided("org.openrewrite:rewrite-maven:${rewriteVersion}")
    provided("org.openrewrite:rewrite-csharp:${rewriteVersion}")
    provided("org.openrewrite:rewrite-scala:${rewriteVersion}")
    provided("org.openrewrite:rewrite-python:${rewriteVersion}")
    provided("org.openrewrite:rewrite-go:${rewriteVersion}")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.apache.groovy:groovy:4.0.33")
    testImplementation("org.junit-pioneer:junit-pioneer:2.+")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation("org.openrewrite:rewrite-javascript:${rewriteVersion}")

    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
}

// The TypeScript tests spawn `npx --package=@openrewrite/rewrite@<version> rewrite-rpc`, and the RPC
// process is shut down between test classes. On a cold npx cache each of those starts its own install
// into the same `~/.npm/_npx` directory, and the resulting overlap leaves the package half-written, so
// the tests fail with "RPC process shut down early". Installing once up front keeps every spawn a
// cache hit.
val warmJavaScriptRpcCache = tasks.register("warmJavaScriptRpcCache") {
    description = "Installs the npm package that the JavaScript RPC tests spawn, so they never race on a cold npx cache."
    val rewriteJavaScriptJars = configurations.named("testRuntimeClasspath")
        .map { classpath -> classpath.filter { it.name.startsWith("rewrite-javascript-") } }
    val marker = layout.buildDirectory.file("tmp/warmJavaScriptRpcCache/version.txt")
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
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}

// The formatters that the recipes in `org.openrewrite.staticanalysis.format` run. Each is bundled into this jar
// as the jars it runs from, under META-INF/rewrite/formatters/<name>/, and loaded at runtime into a class loader
// of its own (see BundledFormatter). They are not dependencies of this module, so that:
// - modules that depend on this one, and target Java 8, do not inherit libraries that need Java 17 or 21,
// - ktfmt keeps the google-java-format 1.x it is built on while the Java recipe runs google-java-format 2.x,
// - google-java-format and palantir-java-format can use javac's internals without `--add-exports`.
// They are pinned, not `latest.release`, because a formatter's output changes between versions, and a recipe
// is meant to produce what a build that pins the same version checks.
val formatterDependencies = mapOf(
    "google-java-format" to "com.google.googlejavaformat:google-java-format:2.0.0",
    "palantir-java-format" to "com.palantir.javaformat:palantir-java-format:2.103.0",
    "ktfmt" to "org.jetbrains.kotlinx:ktfmt:0.65",
)
val formatterConfigurations = formatterDependencies.mapValues { (name, coordinates) ->
    configurations.create("formatter-$name") {
        isCanBeConsumed = false
        isCanBeResolved = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
        dependencies.add(project.dependencies.create(coordinates))
        if (name == "ktfmt") {
            // ktfmt runs on the Kotlin compiler that rewrite-kotlin brings, rather than a second copy of it.
            exclude(group = "org.jetbrains.kotlin")
        }
    }
}
val bundleFormatters by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/formatters")
    val jars = formatterConfigurations.mapValues { (_, configuration) -> configuration.incoming.artifacts.resolvedArtifacts }
    inputs.files(formatterConfigurations.values)
    inputs.property("coordinates", formatterDependencies)
    outputs.dir(outputDir)
    doLast {
        val root = outputDir.get().asFile
        root.deleteRecursively()
        jars.forEach { (name, artifacts) ->
            val dir = root.resolve("META-INF/rewrite/formatters/$name")
            dir.mkdirs()
            val index = StringBuilder("# The jars ").append(name).append(" runs from, in class path order\n")
            index.append("version=").append(formatterDependencies.getValue(name).substringAfterLast(':')).append('\n')
            artifacts.get().forEach { artifact ->
                artifact.file.copyTo(dir.resolve(artifact.file.name), overwrite = true)
                index.append("jar=").append(artifact.file.name).append('\n')
            }
            dir.resolve("index.txt").writeText(index.toString())
        }
    }
}
sourceSets.named("main") {
    resources.srcDir(bundleFormatters)
}
tasks.named<Jar>("sourcesJar") {
    exclude("META-INF/rewrite/formatters/**")
}
