@Suppress("GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-groovy:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-kotlin:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-csharp:${rewriteVersion}")
    implementation("org.openrewrite.meta:rewrite-analysis:${rewriteVersion}")
    implementation("org.apache.commons:commons-text:latest.release")

    annotationProcessor("org.openrewrite:rewrite-templating:${rewriteVersion}")
    implementation("org.openrewrite:rewrite-templating:${rewriteVersion}")
    compileOnly("com.google.errorprone:error_prone_core:2.+:with-dependencies") {
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
