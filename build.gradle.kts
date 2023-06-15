@Suppress("GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

//val rewriteVersion = rewriteRecipe.rewriteVersion.get()
val rewriteVersion = "8.2.0-SNAPSHOT"
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-kotlin:1.2.0-SNAPSHOT")
    implementation("org.openrewrite.meta:rewrite-analysis:2.1.0-SNAPSHOT")
    implementation("org.apache.commons:commons-text:latest.release")

    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.junit-pioneer:junit-pioneer:2.0.0")
    testImplementation("junit:junit:4.13.2")

    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
}
