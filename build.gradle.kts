@file:Suppress("UnstableApiUsage")
import java.io.File
import org.gradle.api.file.RelativePath

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

rewriteRecipe {
    rewriteVersion.set("latest.release")
}

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
    provided("org.openrewrite:rewrite-go:${rewriteVersion}")

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

val rewriteGoRpcClasspath = configurations.detachedConfiguration(
    dependencies.create("org.openrewrite:rewrite-go:${rewriteVersion}")
)
val rewriteGoJar = rewriteGoRpcClasspath.elements.map { artifacts ->
    artifacts.map { it.asFile }.single { it.name.startsWith("rewrite-go-") && it.extension == "jar" }
}
val rewriteGoSourceDir = layout.buildDirectory.dir("rewrite-go-src")
val rewriteGoRpcDir = layout.buildDirectory.dir("rewrite-go-rpc")
val rewriteGoRpcBinary = rewriteGoRpcDir.map { it.file("rewrite-go-rpc") }

val installRewriteGoRpc by tasks.registering(Exec::class) {
    inputs.file(rewriteGoJar)
    outputs.file(rewriteGoRpcBinary)

    doFirst {
        delete(rewriteGoSourceDir)
        copy {
            from(zipTree(rewriteGoJar.get())) {
                include("META-INF/rewrite-go/src/**")
                eachFile {
                    relativePath = RelativePath(true, *relativePath.segments.drop(3).toTypedArray())
                }
                includeEmptyDirs = false
            }
            into(rewriteGoSourceDir)
        }
        rewriteGoRpcDir.get().asFile.mkdirs()
    }

    workingDir(rewriteGoSourceDir)
    commandLine("go", "build", "-o", rewriteGoRpcBinary.get().asFile.absolutePath, "./cmd/rpc")
}

tasks.withType<Test> {
    jvmArgs("-Xmx1g", "-Xms512m")
    dependsOn(installRewriteGoRpc)
    environment("PATH", rewriteGoRpcDir.get().asFile.absolutePath + File.pathSeparator + System.getenv("PATH"))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}
