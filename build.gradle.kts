@Suppress("GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

val rewriteVersion = "latest.release"
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:8.41.1"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-groovy")
    implementation("org.openrewrite:rewrite-kotlin:1.23.1")
    implementation("org.openrewrite:rewrite-csharp:0.16.1")
    implementation("org.openrewrite.meta:rewrite-analysis:2.13.1")
    implementation("org.apache.commons:commons-text:latest.release")

    annotationProcessor("org.openrewrite:rewrite-templating:1.19.1")
    implementation("org.openrewrite:rewrite-templating:1.19.1")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    testImplementation("org.jetbrains:annotations:24.+")
    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.junit-pioneer:junit-pioneer:2.+")
    testImplementation("junit:junit:4.13.2")

    testImplementation("com.google.code.gson:gson:latest.release")

    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
}
