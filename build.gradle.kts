@Suppress("GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    compileOnly("org.projectlombok:lombok:latest.release")
    compileOnly("com.google.errorprone:error_prone_core:2.+:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }
    implementation("org.apache.commons:commons-text:latest.release")
    implementation("org.openrewrite.meta:rewrite-analysis:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-csharp:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-groovy:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-kotlin:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    testImplementation("com.google.code.gson:gson:latest.release")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains:annotations:24.+")
    testImplementation("org.junit-pioneer:junit-pioneer:2.0.1")
    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.projectlombok:lombok:latest.release")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
}
