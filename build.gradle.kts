@Suppress("GradlePackageUpdate")

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.rewrite") version("5.41.0-SNAPSHOT")
}

repositories {
    mavenLocal()
    mavenCentral()
}

group = "org.openrewrite.recipe"
description = "The first Static Analysis and REMEDIATION tool"

val rewriteVersion = "7.41.0-SNAPSHOT"
dependencies {
    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")
    testImplementation("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:${rewriteVersion}"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.apache.commons:commons-text:latest.release")

    testImplementation("org.openrewrite:rewrite-groovy")
    testImplementation("org.junit-pioneer:junit-pioneer:2.0.0")

    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("com.google.code.findbugs:jsr305:latest.release")
}

rewrite {
    // rewriteVersion("latest.integration")
    activeRecipe("org.openrewrite.java.migrate.MigrateToRewrite8")
    // activeRecipe("org.openrewrite.java.format.AutoFormat")
}

